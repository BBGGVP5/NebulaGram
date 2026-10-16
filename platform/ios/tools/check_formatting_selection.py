"""Run the production Foundation-only range helper embedded in the UIKit accessory."""
from pathlib import Path
import subprocess, tempfile

ROOT=Path(__file__).resolve().parents[3]

def check(swift=False):
    path=ROOT/'platform/ios/overlay/submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/NebulaFormattingToolbar.swift'
    source=path.read_text(encoding='utf-8')
    helper=source[source.index('enum NebulaFormattingSelection {'):source.index('/// Native editor actions')]
    assert '[7, 6, 3, 4, 0, 1, 2, 5, 8, 9, 10]' in source
    assert 'tileColor?.isEqual(color)' in source
    if not swift:
        print('iOS formatting range helper, cached tiles and native action order present')
        return
    program='import Foundation\n'+helper+r'''
func test(_ text:String,_ range:Range<Int>,_ expected:Range<Int>?,_ word:Bool=true) {
 precondition(NebulaFormattingSelection.range(text,selection:range,selectWord:word)==expected,text)
}
test("Hello world",11..<11,6..<11);test("Hello world",8..<8,6..<11)
test("Привет мир!  ",13..<13,7..<10);test("one\n你好",6..<6,4..<6)
test("e\u{301}",2..<2,0..<2);test("a🚀b",2..<3,1..<3)
test("🚀",2..<2,nil);test("",0..<0,nil);test("",0..<0,0..<0,false)
test("abc",4..<4,nil);test("abc",1..<2,1..<2)
for i in 0...1000 {test("word \(i)",("word \(i)" as NSString).length..<("word \(i)" as NSString).length,5..<("word \(i)" as NSString).length)}
print("iOS native word selection: empty, bounds, Cyrillic, CJK, combining marks, surrogate pairs and 1001 caret edits passed")
'''
    with tempfile.TemporaryDirectory(prefix='nebula-ios-format-selection-') as directory:
        test=Path(directory)/'FormattingSelection.swift';test.write_text(program,encoding='utf-8')
        subprocess.run(['swift',str(test)],check=True)

if __name__=='__main__':check(True)
