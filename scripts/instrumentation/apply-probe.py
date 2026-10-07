"""Adds the Probe calls to a source tree: around SceneFrames' per-frame callback and BeforeEachDraw's action."""
import shutil, sys, pathlib
root = pathlib.Path(sys.argv[1])
src = root / 'app/src/main/kotlin/my/github/MrxSiN/pixellockscreenevolved'
(src / 'core').mkdir(exist_ok=True)
shutil.copy(pathlib.Path(__file__).with_name('Probe.java'), src / 'core/Probe.java')
def patch(rel, old, new):
    p = src / rel
    s = p.read_text(encoding='utf-8')
    assert old in s, (rel, old)
    p.write_text(s.replace(old, new), encoding='utf-8')
patch('host/SceneFrames.kt', '            onFrame()\n',
      '            my.github.MrxSiN.pixellockscreenevolved.core.Probe.begin(my.github.MrxSiN.pixellockscreenevolved.core.Probe.SCENE_FRAME)\n'
      '            onFrame()\n'
      '            my.github.MrxSiN.pixellockscreenevolved.core.Probe.end(my.github.MrxSiN.pixellockscreenevolved.core.Probe.SCENE_FRAME)\n')
patch('host/BeforeEachDraw.kt', '            action()\n',
      '            my.github.MrxSiN.pixellockscreenevolved.core.Probe.begin(my.github.MrxSiN.pixellockscreenevolved.core.Probe.BEFORE_DRAW)\n'
      '            action()\n'
      '            my.github.MrxSiN.pixellockscreenevolved.core.Probe.end(my.github.MrxSiN.pixellockscreenevolved.core.Probe.BEFORE_DRAW)\n')
