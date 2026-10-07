import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import my.github.MrxSiN.pixellockscreenevolved.depth.SubjectInput;
import my.github.MrxSiN.pixellockscreenevolved.depth.SubjectSegmenter;

/**
 * Device benchmark for the depth effect's segmentation, run with app_process
 * (scripts/bench-segmentation.sh). It times the module's own code stage by
 * stage and end to end, and writes the mask so outputs can be compared.
 *
 * args: <apk> <photo> <runs> <out-mask> [threads] [opt] [ep] [arena] [e2e]
 *   threads 0 = ORT default; opt all|extended|basic|none; ep cpu|xnnpack|nnapi; memory 1 (default) | 0 (no CPU arena) | p (no memory pattern) | 0p (neither);
 *   e2e 1 also runs the module's SubjectSegmenter end to end first (always with its own options), c the same carefully
 */
public final class SegmentationBench {

    public static void main(String[] args) throws Exception {
        String apk = args[0];
        Bitmap decoded = BitmapFactory.decodeFile(args[1]);
        int runs = Integer.parseInt(args[2]);
        String out = args[3];
        int threads = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        String opt = args.length > 5 ? args[5] : "all";
        String ep = args.length > 6 ? args[6] : "cpu";
        String memory = args.length > 7 ? args[7] : "1";
        boolean arena = !memory.contains("0");
        boolean pattern = !memory.contains("p");
        boolean e2e = args.length <= 8 || !args[8].equals("0");
        boolean careful = args.length > 8 && args[8].equals("c");
        // What SystemUI sends: the photo scaled so its long side is 1024 (DepthCutout.forSegmentation).
        float scale = 1024f / Math.max(decoded.getWidth(), decoded.getHeight());
        Bitmap photo = scale >= 1f ? decoded.copy(Bitmap.Config.ARGB_8888, false)
                : Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * scale), Math.round(decoded.getHeight() * scale), true);
        System.out.println("photo " + photo.getWidth() + "x" + photo.getHeight() + " threads " + threads + " opt " + opt + " ep " + ep + " arena " + arena + " pattern " + pattern);
        System.out.println("rss_start_kb " + status("VmRSS"));

        AssetManager assets = AssetManager.class.newInstance();
        AssetManager.class.getMethod("addAssetPath", String.class).invoke(assets, apk);

        if (e2e) {
            // End to end through the module's own class: what the service runs.
            long t0 = SystemClock.elapsedRealtimeNanos();
            SubjectSegmenter segmenter = new SubjectSegmenter(assets);
            System.out.println("e2e_init_ms " + ms(t0));
            Bitmap mask = null;
            for (int i = 0; i < runs; i++) {
                long t = SystemClock.elapsedRealtimeNanos();
                mask = segmenter.maskOf(photo, careful);
                System.out.println("e2e_mask_ms " + ms(t));
            }
            write(mask, out);
            segmenter.close();
            System.out.println("rss_hwm_e2e_kb " + status("VmHWM"));
            if (careful) return;
        }

        // Stage by stage, the same calls SubjectSegmenter makes.
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (threads > 0) options.setIntraOpNumThreads(threads);
        options.setOptimizationLevel(opt.equals("none") ? OrtSession.SessionOptions.OptLevel.NO_OPT
                : opt.equals("basic") ? OrtSession.SessionOptions.OptLevel.BASIC_OPT
                : opt.equals("extended") ? OrtSession.SessionOptions.OptLevel.EXTENDED_OPT
                : OrtSession.SessionOptions.OptLevel.ALL_OPT);
        if (!arena) options.setCPUArenaAllocator(false);
        if (!pattern) options.setMemoryPatternOptimization(false);
        if (ep.equals("xnnpack")) options.addXnnpack(Collections.singletonMap("intra_op_num_threads", String.valueOf(Math.max(threads, 1))));
        if (ep.equals("nnapi")) options.addNnapi();
        long t = SystemClock.elapsedRealtimeNanos();
        ByteBuffer model = map(assets);
        OrtSession session = env.createSession(model, options);
        System.out.println("stage_session_ms " + ms(t));
        String input = session.getInputNames().iterator().next();
        for (int i = 0; i < runs; i++) {
            t = SystemClock.elapsedRealtimeNanos();
            Bitmap scaled = Bitmap.createScaledBitmap(photo, 1024, 1024, true);
            System.out.println("stage_resize_ms " + ms(t));
            t = SystemClock.elapsedRealtimeNanos();
            FloatBuffer pixels = SubjectInput.INSTANCE.of(scaled);
            System.out.println("stage_preprocess_ms " + ms(t));
            t = SystemClock.elapsedRealtimeNanos();
            float[] logits;
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, pixels, new long[]{1, 3, 1024, 1024})) {
                long r = SystemClock.elapsedRealtimeNanos();
                try (OrtSession.Result result = session.run(Collections.singletonMap(input, tensor))) {
                    System.out.println("stage_inference_ms " + ms(r));
                    r = SystemClock.elapsedRealtimeNanos();
                    FloatBuffer buffer = ((OnnxTensor) result.get(0)).getFloatBuffer();
                    logits = new float[buffer.remaining()];
                    buffer.get(logits);
                    System.out.println("stage_output_copy_ms " + ms(r));
                }
            }
            System.out.println("stage_tensor_run_total_ms " + ms(t));
            t = SystemClock.elapsedRealtimeNanos();
            byte[] alpha = SubjectInput.INSTANCE.alphaOf(logits);
            System.out.println("stage_postprocess_ms " + ms(t));
            t = SystemClock.elapsedRealtimeNanos();
            Bitmap small = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ALPHA_8);
            small.copyPixelsFromBuffer(ByteBuffer.wrap(alpha));
            Bitmap mask = Bitmap.createScaledBitmap(small, photo.getWidth(), photo.getHeight(), true);
            System.out.println("stage_mask_ms " + ms(t));
            scaled.recycle();
            small.recycle();
            mask.recycle();
        }
        session.close();
        System.out.println("rss_hwm_kb " + status("VmHWM"));
    }

    private static ByteBuffer map(AssetManager assets) throws Exception {
        android.content.res.AssetFileDescriptor file = assets.openFd("models/birefnet_lite.onnx");
        try (java.nio.channels.FileChannel channel = new java.io.FileInputStream(file.getFileDescriptor()).getChannel()) {
            return channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file.getStartOffset(), file.getLength());
        } finally {
            file.close();
        }
    }

    private static void write(Bitmap mask, String path) throws Exception {
        ByteBuffer raw = ByteBuffer.allocate(mask.getRowBytes() * mask.getHeight());
        mask.copyPixelsToBuffer(raw);
        try (FileOutputStream output = new FileOutputStream(path)) {
            output.write(new byte[]{(byte) (mask.getWidth() >> 8), (byte) mask.getWidth(), (byte) (mask.getHeight() >> 8), (byte) mask.getHeight(), (byte) (mask.getRowBytes() >> 8), (byte) mask.getRowBytes()});
            output.write(raw.array());
        }
    }

    private static String ms(long since) {
        return String.format(java.util.Locale.ROOT, "%.2f", (SystemClock.elapsedRealtimeNanos() - since) / 1e6);
    }

    private static String status(String key) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith(key + ":")) return line.substring(key.length() + 1).trim().split(" ")[0];
            }
        }
        return "?";
    }
}
