"""Import existing Android icon paths as iOS vector assets, with native semantic names."""
from pathlib import Path
import argparse,json,re,xml.etree.ElementTree as ET,subprocess
ROOT=Path(__file__).resolve().parents[1]
DRAW=ROOT/'platform/android/overlay/TMessagesProj/src/main/res/drawable'
OUT=ROOT/'platform/ios/overlay'
A='{http://schemas.android.com/apk/res/android}'
# Shared art is unchanged; Telegram recording/send state assets are deliberately excluded.
ICONS={
'chat':('msg_discussion',['Chat List/Tabs/IconChats','Chat/Context Menu/Chats']),
'person':('msg_contacts',['Chat List/Tabs/IconContacts','Chat/Context Menu/User','Item List/Icons/Profile']),
'gear':('msg_photo_settings',['Chat List/Tabs/IconSettings','Chat/Context Menu/Settings','Chat/Context Menu/Customize']),
'phone':('msg_calls',['Chat List/Tabs/IconCalls','Chat/Context Menu/Call','Chat/Context Menu/PhoneCall','Item List/Icons/Phone']),
'camera':('msg_camera',['Chat List/Tabs/IconCamera','Chat List/AddStoryIcon','Chat/Context Menu/Camera']),
'edit':('msg_edit',['Chat List/ComposeIcon','Chat/Context Menu/Edit']),
'search':('msg_search',['Chat/Context Menu/Search']),
'bookmark':('msg_saved',['Chat/Context Menu/SavedMessages','Item List/Icons/SavedMessages']),
'folder':('files_folder',['Chat/Context Menu/Folder','Item List/Icons/Folder']),
'bell':('msg_notifications',['Chat/Context Menu/Notifications','Item List/Icons/Notifications']),
'lock':('msg_secret',['Chat/Context Menu/Lock','Item List/Icons/Privacy']),
'video':('msg_videocall',['Chat/Context Menu/VideoCall','Item List/Icons/Video']),
'attach':('input_attach',['Chat/Input/Text/IconAttachment']),
'photo':('msg_gallery',['Chat/Context Menu/Photo','Item List/Icons/Photo']),
'archive':('msg_archive',['Chat/Context Menu/Archive']),
'copy':('msg_copy',['Chat/Context Menu/Copy']),
'trash':('msg_delete',['Chat/Context Menu/Delete']),
'share':('msg_share',['Chat/Context Menu/Share']),
'globe':('msg_language',['Item List/Icons/Language']),
'info':('msg_info',['Chat/Context Menu/Info']),
'list':('msg_list',['Chat/Context Menu/Topics']),
'download':('msg_download',['Chat/Context Menu/Download']),
'file':('msg_sendfile',['Chat/Context Menu/File','Item List/Icons/File']),
'more':(None,['Chat List/NavigationMore']),
'mic':(None,['Chat/Input/Text/IconMicrophone']),
'forward':(None,['Chat/Context Menu/Forward']),
}
SYMBOLS={'bubble.left.and.bubble.right':'chat','bubble.left':'chat','person.crop.circle':'person','gearshape':'gear','gear':'gear','phone':'phone','camera':'camera','pencil':'edit','magnifyingglass':'search','bookmark':'bookmark','folder':'folder','bell':'bell','lock':'lock','lock.shield':'lock','shield':'lock','video':'video','paperclip':'attach','photo':'photo','archivebox':'archive','doc.on.doc':'copy','trash':'trash','square.and.arrow.up':'share','globe':'globe','info.circle':'info','list.bullet':'list','arrow.down':'download','arrow.down.circle':'download','doc':'file'}
def svg(source):
 root=ET.parse(source).getroot(); w=root.attrib[A+'viewportWidth']; h=root.attrib[A+'viewportHeight']
 out=ET.Element('svg',{'xmlns':'http://www.w3.org/2000/svg','width':'24','height':'24','viewBox':f'0 0 {w} {h}'})
 for node in root:
  if node.tag!='path':raise ValueError(f'Unexpected vector group {source}')
  attrs={'d':node.attrib[A+'pathData'],'fill':'none'}
  for a,b in [('fillColor','fill'),('strokeColor','stroke'),('strokeWidth','stroke-width'),('strokeLineCap','stroke-linecap'),('strokeLineJoin','stroke-linejoin'),('strokeAlpha','stroke-opacity'),('fillAlpha','fill-opacity')]:
   if A+a in node.attrib:
    v=node.attrib[A+a]
    if a.endswith('Color'):
     if v=='@android:color/transparent':v='none'
     elif len(v)==9:
      opacity=int(v[1:3],16)/255
      if opacity==0:v='none'
      else:v='#'+v[3:];attrs[b+'-opacity']=str(opacity)
    attrs[b]=v
  if node.attrib.get(A+'fillType')=='evenOdd':attrs['fill-rule']='evenodd'
  ET.SubElement(out,'path',attrs)
 return ET.tostring(out,encoding='unicode')+'\n'
def generate():
 outputs={};maps=[]
 existing=set(subprocess.check_output(['git','-C',str(ROOT/'vendor/telegram-ios'),'ls-tree','-r','--name-only','HEAD','submodules/TelegramUI/Images.xcassets'],text=True).splitlines())
 for semantic,(solar,names) in ICONS.items():
  for pack,filename in [(1,'nebula_cupertino_'+semantic),(2,'nebula_'+solar+'_solar' if solar else '')]:
   if not filename:continue
   source=DRAW/(filename+'.xml')
   assert source.exists(),source
   folder=OUT/f'submodules/TelegramUI/Images.xcassets/NebulaPack{pack}_{semantic}.imageset'
   outputs[folder/'icon.svg']=svg(source)
   outputs[folder/'Contents.json']=json.dumps({'images':[{'filename':'icon.svg','idiom':'universal'}],'info':{'author':'xcode','version':1},'properties':{'preserves-vector-representation':True,'template-rendering-intent':'template'}},indent=2)+'\n'
  valid=[n for n in names if f'submodules/TelegramUI/Images.xcassets/{n}.imageset/Contents.json' in existing]
  # Some semantics are used only by NebulaSettingsStyle.
  for name in valid:maps.append((name,semantic,solar is not None))
 header='// Generated by scripts/generate-ios-icon-packs.py.\nstatic NSDictionary<NSString *, NSArray<NSString *> *> *NebulaIconNames(void) {\n    static NSDictionary *names;\n    static dispatch_once_t onceToken;\n    dispatch_once(&onceToken, ^{ names = @{\n'
 for name,semantic,has_solar in maps:header+=f'        @"{name}": @[@"NebulaPack1_{semantic}", @"'+(f'NebulaPack2_{semantic}' if has_solar else '')+'"],\n'
 header+='    }; });\n    return names;\n}\n'
 outputs[OUT/'submodules/AppBundle/Sources/AppBundle/NebulaIconPacks.h']=header
 swift='// Generated by scripts/generate-ios-icon-packs.py.\nimport UIKit\nimport AppBundle\n\nenum NebulaIconPackArtwork {\n    static func image(symbol: String, pack: Int) -> UIImage? {\n        let name: String\n        switch symbol {\n'
 for symbol,semantic in SYMBOLS.items():swift+=f'        case "{symbol}": name = "{semantic}"\n'
 swift+='        default: return nil\n        }\n        if let imported = NebulaImportedIcons.image(semantic: name) { return imported }\n        guard pack == 1 || pack == 2 else { return nil }\n        return UIImage(bundleImageName: "NebulaPack\\(pack)_\\(name)")\n    }\n}\n'
 outputs[OUT/'submodules/SettingsUI/Sources/NebulaIconPackArtwork.swift']=swift
 return outputs
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
 outputs=generate()
 for path,text in outputs.items():
  if args.check:assert path.exists() and path.read_text(encoding='utf-8')==text,path
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8',newline='\n')
 print(f'OK: {len(outputs)} iOS vector/map files match Android source paths')

