#!/usr/bin/env python3
"""Generate the checked-in HeadUp Xcode project using only Python's standard library."""
import hashlib
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parent.parent
objects = {}
# Keep signing choices made in Xcode local when regenerating the source inventory.
project_file = ROOT/'HeadUp.xcodeproj'/'project.pbxproj'
previous = {}
if project_file.exists():
    previous = json.loads(subprocess.check_output(['plutil', '-convert', 'json', '-o', '-', str(project_file)]))['objects']

def ident(label):
    return hashlib.sha1(label.encode()).hexdigest()[:24].upper()

def obj(label, isa, **values):
    key = ident(label)
    objects[key] = dict(isa=isa, **values)
    return key

def encode(value, depth=0):
    if isinstance(value, dict):
        return '{\n' + ''.join('\t'*(depth+1)+json.dumps(k)+' = '+encode(v,depth+1)+';\n' for k,v in value.items())+'\t'*depth+'}'
    if isinstance(value, list):
        return '(\n'+''.join('\t'*(depth+1)+encode(v,depth+1)+',\n' for v in value)+'\t'*depth+')'
    return json.dumps(str(value))

app_product = obj('app-product','PBXFileReference',explicitFileType='wrapper.application',path='HeadUp.app',sourceTree='BUILT_PRODUCTS_DIR')
test_product = obj('test-product','PBXFileReference',explicitFileType='wrapper.cfbundle',path='HeadUpTests.xctest',sourceTree='BUILT_PRODUCTS_DIR')
products = obj('products','PBXGroup',children=[app_product,test_product],name='Products',sourceTree='<group>')
groups = [products]
phases = {}
for target, folder in [('app','HeadUp'),('tests','HeadUpTests')]:
    files, sources, resources = [], [], []
    for path in sorted((ROOT/folder).rglob('*')):
        if not path.is_file() or path.name.startswith('.') or path.suffix not in ['.swift','.mp4','.task','.jpg','.xcprivacy']:
            continue
        relative = str(path.relative_to(ROOT/folder))
        kind = {'.swift':'sourcecode.swift','.jpg':'image.jpeg','.mp4':'file','.task':'file','.xcprivacy':'text.xml'}[path.suffix]
        ref = obj(folder+'/'+relative,'PBXFileReference',lastKnownFileType=kind,path=relative,sourceTree='<group>')
        files.append(ref)
        build = obj('build/'+folder+'/'+relative,'PBXBuildFile',fileRef=ref)
        (sources if path.suffix=='.swift' else resources).append(build)
    if target == 'app':
        ref = obj('assets','PBXFileReference',lastKnownFileType='folder.assetcatalog',path='Assets.xcassets',sourceTree='<group>')
        files.append(ref)
        resources.append(obj('assets-build','PBXBuildFile',fileRef=ref))
    groups.append(obj(folder,'PBXGroup',children=files,path=folder,sourceTree='<group>'))
    phases[target] = [obj(target+'-sources','PBXSourcesBuildPhase',buildActionMask=2147483647,files=sources,runOnlyForDeploymentPostprocessing=0),
                      obj(target+'-resources','PBXResourcesBuildPhase',buildActionMask=2147483647,files=resources,runOnlyForDeploymentPostprocessing=0)]

# Lightweight ActivityKit contract and intent are compiled into app + widget.
shared_files, shared_builds = [], []
for path in sorted((ROOT/'HeadUpActivityShared').glob('*.swift')):
    ref = obj('shared/'+path.name,'PBXFileReference',lastKnownFileType='sourcecode.swift',path=path.name,sourceTree='<group>')
    shared_files.append(ref)
    shared_builds.append(obj('shared-app/'+path.name,'PBXBuildFile',fileRef=ref))
groups.append(obj('shared-group','PBXGroup',children=shared_files,path='HeadUpActivityShared',sourceTree='<group>'))
objects[phases['app'][0]]['files'].extend(shared_builds)
widget_files, widget_sources = [], []
for path in sorted((ROOT/'HeadUpWidgets').glob('*.swift')):
    ref = obj('widget/'+path.name,'PBXFileReference',lastKnownFileType='sourcecode.swift',path=path.name,sourceTree='<group>')
    widget_files.append(ref)
    widget_sources.append(obj('widget-build/'+path.name,'PBXBuildFile',fileRef=ref))
for ref in shared_files:
    widget_sources.append(obj('widget-shared/'+ref,'PBXBuildFile',fileRef=ref))
widget_assets = obj('widget-assets','PBXFileReference',lastKnownFileType='folder.assetcatalog',path='Assets.xcassets',sourceTree='<group>')
widget_files.append(widget_assets)
groups.append(obj('widget-group','PBXGroup',children=widget_files,path='HeadUpWidgets',sourceTree='<group>'))
widget_phases = [obj('widget-sources','PBXSourcesBuildPhase',buildActionMask=2147483647,files=widget_sources,runOnlyForDeploymentPostprocessing=0),
                 obj('widget-resources','PBXResourcesBuildPhase',buildActionMask=2147483647,files=[obj('widget-assets-build','PBXBuildFile',fileRef=widget_assets)],runOnlyForDeploymentPostprocessing=0)]

remote = obj('mediapipe','XCRemoteSwiftPackageReference',repositoryURL='https://github.com/google-ai-edge/mediapipe.git',requirement={'kind':'revision','revision':'50505c8312c7eacb1bd411cecdc4cc9fd08d53cb'})
local = obj('core','XCLocalSwiftPackageReference',relativePath='HeadUpCore')
dependencies = {}
for target in ['app','tests']:
    dependencies[target] = []
    builds = []
    for name, package in [('HeadUpCore',local),('MediaPipeTasksVision',remote)]:
        if target == 'tests' and name == 'MediaPipeTasksVision': continue
        dep = obj(target+'-'+name,'XCSwiftPackageProductDependency',package=package,productName=name)
        dependencies[target].append(dep)
        builds.append(obj(target+'-link-'+name,'PBXBuildFile',productRef=dep))
    phases[target].append(obj(target+'-frameworks','PBXFrameworksBuildPhase',buildActionMask=2147483647,files=builds,runOnlyForDeploymentPostprocessing=0))

common = {'IPHONEOS_DEPLOYMENT_TARGET':'17.0','SDKROOT':'iphoneos','SWIFT_VERSION':'5.0','CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','ENABLE_USER_SCRIPT_SANDBOX':'YES','SWIFT_STRICT_CONCURRENCY':'targeted'}
common['ARCHS'] = 'arm64'
common['ONLY_ACTIVE_ARCH'] = 'YES'
app_settings = {'PRODUCT_BUNDLE_IDENTIFIER':'org.anglezero.HeadUp','PRODUCT_NAME':'HeadUp','GENERATE_INFOPLIST_FILE':'YES','INFOPLIST_KEY_CFBundleDisplayName':'HeadUp','INFOPLIST_FILE':'HeadUp/Info.plist','INFOPLIST_KEY_NSCameraUsageDescription':'HeadUp uses the camera to analyze posture on this device, including while your camera window is visible over other apps. / HeadUp 使用相機在本機分析姿勢，也可在其他 App 上方顯示相機小視窗時持續偵測。','INFOPLIST_KEY_UIApplicationSceneManifest_Generation':'YES','INFOPLIST_KEY_UILaunchScreen_Generation':'YES','INFOPLIST_KEY_UISupportedInterfaceOrientations':'UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight','INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad':'UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight','TARGETED_DEVICE_FAMILY':'1,2','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','SUPPORTS_MACCATALYST':'NO','CODE_SIGN_STYLE':'Automatic','MARKETING_VERSION':'1.0.0','CURRENT_PROJECT_VERSION':'1','ASSETCATALOG_COMPILER_APPICON_NAME':'AppIcon','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks']}
test_settings = {'PRODUCT_BUNDLE_IDENTIFIER':'org.anglezero.HeadUpTests','PRODUCT_NAME':'$(TARGET_NAME)','GENERATE_INFOPLIST_FILE':'YES','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic','BUNDLE_LOADER':'$(TEST_HOST)','TEST_HOST':'$(BUILT_PRODUCTS_DIR)/HeadUp.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/HeadUp','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@loader_path/Frameworks']}
app_settings['OTHER_LDFLAGS'] = ['$(inherited)', '-framework', 'UIKit', '-framework', 'Metal', '-framework', 'OpenGLES']
def configs(label, settings):
    refs=[]
    for name in ['Debug','Release','PiPPreview']:
        values=dict(settings)
        values.update({'SWIFT_OPTIMIZATION_LEVEL':'-Onone' if name!='Release' else '-O','DEBUG_INFORMATION_FORMAT':'dwarf' if name!='Release' else 'dwarf-with-dsym'})
        if name!='Release': values.update({'ENABLE_TESTABILITY':'YES','SWIFT_ACTIVE_COMPILATION_CONDITIONS':'DEBUG $(inherited)'})
        if name == 'PiPPreview':
            values['IPHONEOS_DEPLOYMENT_TARGET'] = '18.0'
            values['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = 'DEBUG HEADUP_PIP_PREVIEW $(inherited)'
            if label == 'app': values['INFOPLIST_FILE'] = 'HeadUp/Info-PiPPreview.plist'
        if label == 'widget':
            values['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = values.get('SWIFT_ACTIVE_COMPILATION_CONDITIONS','$(inherited)') + ' HEADUP_WIDGET'
        saved = previous.get(ident(label+name), previous.get(ident(label+'Debug'), {})).get('buildSettings', {})
        for key in ['DEVELOPMENT_TEAM', 'CODE_SIGN_IDENTITY', 'PROVISIONING_PROFILE_SPECIFIER', 'PRODUCT_BUNDLE_IDENTIFIER', 'CODE_SIGN_STYLE']:
            if key in saved: values[key] = saved[key]
        refs.append(obj(label+name,'XCBuildConfiguration',buildSettings=values,name=name))
    return obj(label+'-configs','XCConfigurationList',buildConfigurations=refs,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')
project_key = ident('project')
app_key = obj('app-target','PBXNativeTarget',buildConfigurationList=configs('app',app_settings),buildPhases=phases['app'],buildRules=[],dependencies=[],name='HeadUp',packageProductDependencies=dependencies['app'],productName='HeadUp',productReference=app_product,productType='com.apple.product-type.application')
proxy = obj('test-proxy','PBXContainerItemProxy',containerPortal=project_key,proxyType=1,remoteGlobalIDString=app_key,remoteInfo='HeadUp')
dependency = obj('test-dependency','PBXTargetDependency',target=app_key,targetProxy=proxy)
test_key = obj('test-target','PBXNativeTarget',buildConfigurationList=configs('test',test_settings),buildPhases=phases['tests'],buildRules=[],dependencies=[dependency],name='HeadUpTests',packageProductDependencies=dependencies['tests'],productName='HeadUpTests',productReference=test_product,productType='com.apple.product-type.bundle.unit-test')
ui_product = obj('ui-product','PBXFileReference',explicitFileType='wrapper.cfbundle',path='HeadUpUITests.xctest',sourceTree='BUILT_PRODUCTS_DIR')
objects[products]['children'].append(ui_product)
ui_file = obj('ui-file','PBXFileReference',lastKnownFileType='sourcecode.swift',path='HeadUpUITests.swift',sourceTree='<group>')
groups.append(obj('ui-group','PBXGroup',children=[ui_file],path='HeadUpUITests',sourceTree='<group>'))
ui_source = obj('ui-sources','PBXSourcesBuildPhase',buildActionMask=2147483647,files=[obj('ui-build','PBXBuildFile',fileRef=ui_file)],runOnlyForDeploymentPostprocessing=0)
ui_settings = {'PRODUCT_BUNDLE_IDENTIFIER':'org.anglezero.HeadUpUITests','PRODUCT_NAME':'$(TARGET_NAME)','GENERATE_INFOPLIST_FILE':'YES','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic','TEST_TARGET_NAME':'HeadUp'}
ui_key = obj('ui-target','PBXNativeTarget',buildConfigurationList=configs('ui',ui_settings),buildPhases=[ui_source],buildRules=[],dependencies=[dependency],name='HeadUpUITests',productName='HeadUpUITests',productReference=ui_product,productType='com.apple.product-type.bundle.ui-testing')
root_group = obj('root-group','PBXGroup',children=groups,sourceTree='<group>')
obj('project','PBXProject',attributes={'BuildIndependentTargetsInParallel':'YES','LastUpgradeCheck':'1600','TargetAttributes':{app_key:{'CreatedOnToolsVersion':'16.0'},test_key:{'CreatedOnToolsVersion':'16.0','TestTargetID':app_key}}},buildConfigurationList=configs('project',common),compatibilityVersion='Xcode 14.0',developmentRegion='en',hasScannedForEncodings=0,knownRegions=['en','zh-Hant','Base'],mainGroup=root_group,packageReferences=[remote,local],productRefGroup=products,projectDirPath='',projectRoot='',targets=[app_key,test_key])
objects[project_key]['targets'].append(ui_key)
widget_product = obj('widget-product','PBXFileReference',explicitFileType='wrapper.app-extension',path='HeadUpWidgets.appex',sourceTree='BUILT_PRODUCTS_DIR')
objects[products]['children'].append(widget_product)
app_debug = objects[ident('appDebug')]['buildSettings']
widget_settings = {'PRODUCT_NAME':'HeadUpWidgets','PRODUCT_BUNDLE_IDENTIFIER':app_debug['PRODUCT_BUNDLE_IDENTIFIER']+'.Widgets',
                   'GENERATE_INFOPLIST_FILE':'YES','INFOPLIST_FILE':'HeadUpWidgets/Info.plist','TARGETED_DEVICE_FAMILY':'1,2',
                   'CODE_SIGN_STYLE':'Automatic','APPLICATION_EXTENSION_API_ONLY':'YES','SKIP_INSTALL':'YES',
                   'MARKETING_VERSION':'1.0.0','CURRENT_PROJECT_VERSION':'1',
                   'LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@executable_path/../../Frameworks']}
if 'DEVELOPMENT_TEAM' in app_debug: widget_settings['DEVELOPMENT_TEAM'] = app_debug['DEVELOPMENT_TEAM']
widget_key = obj('widget-target','PBXNativeTarget',buildConfigurationList=configs('widget',widget_settings),buildPhases=widget_phases,
                 buildRules=[],dependencies=[],name='HeadUpWidgets',productName='HeadUpWidgets',productReference=widget_product,productType='com.apple.product-type.app-extension')
objects[project_key]['targets'].append(widget_key)
widget_proxy = obj('widget-proxy','PBXContainerItemProxy',containerPortal=project_key,proxyType=1,remoteGlobalIDString=widget_key,remoteInfo='HeadUpWidgets')
objects[app_key]['dependencies'].append(obj('widget-dependency','PBXTargetDependency',target=widget_key,targetProxy=widget_proxy))
embed_build = obj('embed-widget-build','PBXBuildFile',fileRef=widget_product,settings={'ATTRIBUTES':['RemoveHeadersOnCopy']})
objects[app_key]['buildPhases'].append(obj('embed-widget','PBXCopyFilesBuildPhase',buildActionMask=2147483647,dstPath='',dstSubfolderSpec=13,
                                         files=[embed_build],runOnlyForDeploymentPostprocessing=0,name='Embed App Extensions'))
project = ROOT/'HeadUp.xcodeproj'
project.mkdir(exist_ok=True)
workspace = project/'project.xcworkspace'
lockdir = workspace/'xcshareddata'/'swiftpm'
lockdir.mkdir(parents=True,exist_ok=True)
(workspace/'contents.xcworkspacedata').write_text('<?xml version="1.0" encoding="UTF-8"?><Workspace version="1.0"><FileRef location="self:"/></Workspace>\n')
(lockdir/'Package.resolved').write_text(json.dumps({'pins':[{'identity':'mediapipe','kind':'remoteSourceControl','location':'https://github.com/google-ai-edge/mediapipe.git','state':{'revision':'50505c8312c7eacb1bd411cecdc4cc9fd08d53cb'}}],'version':2},indent=2)+'\n')
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+encode({'archiveVersion':1,'classes':{},'objectVersion':60,'objects':objects,'rootObject':project_key})+'\n')
schemes = project/'xcshareddata'/'xcschemes'
schemes.mkdir(parents=True,exist_ok=True)
def reference(key, name, product):
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{key}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:HeadUp.xcodeproj"/>'
app_ref=reference(app_key,'HeadUp','HeadUp.app')
test_ref=reference(test_key,'HeadUpTests','HeadUpTests.xctest')
ui_ref=reference(ui_key,'HeadUpUITests','HeadUpUITests.xctest')
(schemes/'HeadUp.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{app_ref}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{test_ref}</TestableReference><TestableReference skipped="NO">{ui_ref}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>''')
# Preview runs locally with the iOS 18 VoIP multitasking-camera capability. Archive
# deliberately remains Release and does not carry that experimental declaration.
preview_scheme = (schemes/'HeadUp.xcscheme').read_text().replace('buildConfiguration="Debug"', 'buildConfiguration="PiPPreview"')
(schemes/'HeadUp PiP Preview.xcscheme').write_text(preview_scheme)
print(project)
