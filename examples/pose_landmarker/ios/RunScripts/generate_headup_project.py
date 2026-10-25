#!/usr/bin/env python3
"""Generate the checked-in HeadUp Xcode project using only Python's standard library."""
import hashlib
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent.parent
objects = {}

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
app_settings = {'PRODUCT_BUNDLE_IDENTIFIER':'org.anglezero.HeadUp','PRODUCT_NAME':'HeadUp','GENERATE_INFOPLIST_FILE':'YES','INFOPLIST_KEY_CFBundleDisplayName':'HeadUp','INFOPLIST_KEY_NSCameraUsageDescription':'HeadUp uses the camera to analyze your posture on this device while the app is open. / HeadUp 僅在 App 開啟時使用相機，在本機分析姿勢。','INFOPLIST_KEY_UIApplicationSceneManifest_Generation':'YES','INFOPLIST_KEY_UILaunchScreen_Generation':'YES','INFOPLIST_KEY_UISupportedInterfaceOrientations':'UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight','INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad':'UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight','TARGETED_DEVICE_FAMILY':'1,2','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','SUPPORTS_MACCATALYST':'NO','CODE_SIGN_STYLE':'Automatic','MARKETING_VERSION':'1.0.0','CURRENT_PROJECT_VERSION':'1','ASSETCATALOG_COMPILER_APPICON_NAME':'AppIcon','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks']}
test_settings = {'PRODUCT_BUNDLE_IDENTIFIER':'org.anglezero.HeadUpTests','PRODUCT_NAME':'$(TARGET_NAME)','GENERATE_INFOPLIST_FILE':'YES','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic','BUNDLE_LOADER':'$(TEST_HOST)','TEST_HOST':'$(BUILT_PRODUCTS_DIR)/HeadUp.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/HeadUp','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@loader_path/Frameworks']}
app_settings['OTHER_LDFLAGS'] = ['$(inherited)', '-framework', 'UIKit', '-framework', 'Metal', '-framework', 'OpenGLES']
def configs(label, settings):
    refs=[]
    for name in ['Debug','Release']:
        values=dict(settings)
        values.update({'SWIFT_OPTIMIZATION_LEVEL':'-Onone' if name=='Debug' else '-O','DEBUG_INFORMATION_FORMAT':'dwarf' if name=='Debug' else 'dwarf-with-dsym'})
        if name=='Debug': values.update({'ENABLE_TESTABILITY':'YES','SWIFT_ACTIVE_COMPILATION_CONDITIONS':'DEBUG $(inherited)'})
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
print(project)
