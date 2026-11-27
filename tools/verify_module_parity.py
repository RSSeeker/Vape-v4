#!/usr/bin/env python3
"""Audit the English build against the pinned v4.21.58 source and EXE.

No executable is run: the two PE resources (EXE -> DLL -> JAR) are read as
bytes. This check verifies inventories and compiled class/resource equality;
it cannot replace a Minecraft runtime test or prove identical behavior.
"""

import argparse
from collections import Counter
import hashlib
import io
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile


ORIGINAL_SHA256 = "9b77e8ae1bddb6296e5d773d19ecada341fe93ac8ef39559bddcca7a4b945482"
BASE_COMMIT = "36aa9fb4890b2845de3452f008a334d0d08b774f"

# Reviewed Soulbound presentation and compatibility changes.
# Pin their complete contents so an unrelated change cannot hide inside an
# approved file. Deliberate future changes require a fresh parity review.
APPROVED_SOURCES = {
    'gg/vape/Vape.java': '3e77c5f28dcc261dc9c5c7cb08b25bc8b5bb2321f0d7a225525699eb76905f05',
    'gg/vape/config/ClientSettings.java': 'bee68422892ef001250a86f8850db82e52e8acd8a6a7ec9812422db62642b9b4',
    'gg/vape/config/LocalConfigStore.java': 'ac9e0cf033478b788655455b4a4369a12b5778c6d6389638a10b26c96246d66e',
    'gg/vape/config/PublicProfileSettings.java': 'ad84092706c242e2ec7f02a7a8855a35f11cb25f95cf1e584c317e051e430b3b',
    'gg/vape/deeplearn/DeepLearningEngine.java': 'aa44a1de27dc639faf4c6e29613a0a8c9e69be963a6399af367bc743463507d4',
    'gg/vape/deeplearn/ModelManager.java': 'e24f35696602125ab6e3105e98b26a1cccbc14ed34b29ee6c8ea3ed02b4a39ac',
    'gg/vape/deeplearn/models/MlpModel.java': '9a0aa6a189f33bbad2d742a2e9d53da58e5a0af812b226476f11e3c158f80bf6',
    'gg/vape/event/impl/EventRenderWorldPassExecutorDrain.java': '64f504db1a0f4d4d366edeae82e05c08de59ef1d29802e1a0cb92d380b3f2991',
    'gg/vape/friend/ui/FriendRequestsPanel.java': '2741855e50f8d6efc8cc1b419d2a7613e86f80e5354bef46a3a3117d8d2f69d7',
    'gg/vape/friend/ui/OnlineFriendsFrame.java': '040cbcff7e7deea5509a008767af59ff16230501893c04b76e2fd19fd60a47a2',
    'gg/vape/friend/ui/PartyMemberEntryComponent.java': '059d6af750c1fbffa3a8deb6cd4ed210c812d3d3eceb7a5c7feceb648f9d3691',
    'gg/vape/manager/client/GlobalSettingsController.java': '8ab4935e5b7953c4b6f447532c723c3e422d46443409ba1d5055ad29dce949d5',
    'gg/vape/manager/client/OnlineConnectionManager.java': 'e650a98d4557f9015513504cf66c8616d6f6ec46221869e6bd78d29a75fd58bc',
    'gg/vape/manager/client/OnlineSettings.java': '9a229c88997251da6fe98d6ace81fc0e7c4291c2f68ad8323fd51c1b771d3b13',
    'gg/vape/mapping/JavassistMappingTask.java': '4d844a830966f5b5b8975e33438a6856b54b204dec1c9e78a7b147df24641e74',
    'gg/vape/module/none/ClientSettings.java': '92dc37e0c17b116fbc754ae51223aa2c7c81cc292d69d8bd5ab801d8bd7fa893',
    'gg/vape/notification/NotificationSoundPlayer.java': 'c4e7599566ec5c761b88514ed55318a2ae16f07ca9e51a83f830ceed290aa99e',
    'gg/vape/runtime/NativeBridge.java': 'ca5f8024b13b55a1143090742859dc7605d94962f8eae74fb78c35939c0b23de',
    'gg/vape/service/VapeServiceApplication.java': '5fc18c60a7c9a41e5fdcc8573fefaa794c59e22a90b0e2d5c7cc8dc948106b1b',
    'gg/vape/service/VapeServiceLauncher.java': '04698294bcbc0dbcc2652065f631612be077dc83a33ec28d0fe1a57bc31fe228',
    'gg/vape/service/http/LegacyHttpServer.java': 'e9adde861499250586df6cb0aa792a1acf9b9ebc39fc8d1c5ea95eb357b4d701',
    'gg/vape/sync/SyncThread.java': '7b35b67410dc53cee647c63209ce7238e1b2997926f5ed8da5ca4b7c5a5e96ee',
    'gg/vape/tutorial/TutorialWelcomePanel.java': '30830b3c226d057d7b67130828e417be90e4996c7f0331d95ed9cb1861ed207a',
    'gg/vape/tutorial/page/HudTutorialPage.java': 'cd9738a9375bb8b0eb95c14d7104a54534714c53b88ea55788ce1f21352edecb',
    'gg/vape/tutorial/page/ModulesTutorialPage.java': '37a74b9553ef02097cd2d532e452b6f9450a83100a9ccf7154e766303472e3e1',
    'gg/vape/ui/click/frame/impl/ClientSettingsComponentFactory.java': '49d33ccc857bbbeda5c87d26cbd06aede0bfdd93ef08d7ec26bbde0413d10f3e',
    'gg/vape/ui/click/frame/impl/ClientSettingsFrame.java': '923236c0a25927b9c3e1d3b037a4ad34ae7e575a19cc9ec6b6eee5bd3979f8a0',
    'gg/vape/ui/click/frame/impl/ClientSettingsSearchFrameHeader.java': 'f408a22ab098bd8f0c7563fba2754934cd5b6bf7f9a20398188be80ef8b8a4fc',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsFriendCardFactory.java': 'd255b0517737282afd4c8117a761a16d05e2532d22d1047060246467e222d0fb',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsPage.java': '7326a8e5db489ccdff3501cf0d271e6acecf92a56dc4fae24b67d61073224c19',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsThemeConfigFactory.java': 'e3ff595d09aa11ea08fc8b0e6b713db6d41e9d5d72b21b75d749754f9b19e5a8',
    'gg/vape/ui/click/frame/impl/main/ClickGuiMainFrameHeaderActionComponent.java': 'bd03b966196cc15b1afd7df6a5c8ba2239a8916fc7f0d9676d0dbcb7bf70003c',
    'gg/vape/ui/click/frame/impl/main/ClickGuiOverlaySpecFactoryA.java': '1a6749770e02f3ea4069179e9a4ad3c4c6458bcb1d7fefbea5c7e5306fa58cf0',
    'gg/vape/ui/click/frame/impl/online/OnlineAccountLinkCodePageComponent.java': '16d71536891cb633ba2102d75127a7ba13a1ab81d71a10d2d104590ca4a0597f',
    'gg/vape/ui/click/frame/impl/online/OnlineAccountUnavailablePageComponent.java': '2d3d6db6467cb0ed0fab7d5e90462ee95932b4bf796a3dcc4800e0131872471a',
    'gg/vape/ui/click/frame/impl/online/OnlineConnectionRetryPageComponent.java': 'c6972148235f2731fe093abfaf8edf685c64bee18f4c4bf335ad9da77815b548',
    'gg/vape/ui/click/frame/impl/online/OnlineConnectionSettingsFrame.java': '2d22448183ea03980cd4ac2c0ec3914156b393448892ad3436976c908c4c4587',
    'gg/vape/ui/theme/ThemeColors.java': 'ce78da1e6f6acaa210d7c7ded4c83e6acc255be489737060ead8c534dd728254',
    'gg/vape/utils/network/AnticheatProfile.java': '1954e7755d9e936b2264b93a22836eeadf7c678f584510ba292e2c72d626bd8d',
    'gg/vape/utils/network/OutboundPacketPipeline.java': '1b991f5cd372546b2412b71cca30f8985834fa35fddd71e902ff04d0074be5b7',
    'gg/vape/utils/render/OpenGlDeviceInfo.java': 'abeb2fc96b3d007dad7405d79d17722213f7574fde98dc52a6e705eea04fdcfe',
    'gg/vape/utils/render/RemoteImageHttpDownloader.java': 'ce96f2650d5c398133ce2840f7d7a53a3d67789195fde02d06f817c8bc187890',
    'gg/vape/utils/render/RenderBatchManager.java': '645b1e34c96cf27758314f80cde946fa855e67c5f04abcfac94bdce02432be3b',
}

# Reviewed behavioral improvements, module branding edits, and additions.
# Complete content hashes remain mandatory.
APPROVED_ENHANCEMENTS = {
    'gg/vape/brand/SoulboundBrand.java': '46b0084f91db697bbdebc136f531f6a3027a26a349555e11aeb9c98ae48ad920',
    'gg/vape/brand/SoulboundPaths.java': '0839147a28ebb876b7eaca3b9ec3d4c75198b7886935eaff63a14f6ac2af9450',
    'gg/vape/input/AttackKeyController.java': '38d5baeefc4cb441fde2c9823628f53ae031a3f46b01a179db1caf1fb2f31d8c',
    'gg/vape/module/combat/AimAssist.java': '2a81e9bdfecf9a363cf4414ea138b658e203e234fd216c9ec016cd5a0d22ee35',
    'gg/vape/module/combat/AutoMace.java': 'f8b0956f993952eb7fd93ae6a52a9e52905c8a370e76d19ec7ccb3d75c45319f',
    'gg/vape/module/combat/HitSwap.java': 'c760686e00f000379f849964ad5fa718d776bfd55f4732c787692a4a32c5e922',
    'gg/vape/module/combat/SilentAura.java': '1dba329826258d86e43e30cf0d0498c3e9a6cfe56f491446451bbb5d187a91c2',
    'gg/vape/module/combat/Triggerbot.java': '6be6b8b2590fdf29a2a3a8eb08fd2d5fbc8f819abee3e0c1601ab244e87240c7',
    'gg/vape/module/combat/aimassist/AimAssistKissSubModule.java': '31ff65af290fb0af673f23383a362eb9e71321dfd30be131456560565062247c',
    'gg/vape/module/combat/aimassist/AimAssistRotationSubModule.java': 'a9b4a0bb9392facc5029d165d9f8936981012609079aa52f4c57570c7e6f1686',
    'gg/vape/module/combat/aimassist/AimAssistTargetingSubModule.java': '8bebb812eece35f0fe667ff4b8f6f3de429c4751c6fb4179684c08cedc4a8a0c',
    'gg/vape/module/combat/automace/AimSelectionRules.java': '8b25748d4dd7a2db2697c6397f86c428f7ee8c1f38aa202996f2674e480b946d',
    'gg/vape/module/combat/automace/AutoMaceAttackGate.java': '6384c92e5d2339f8122c917dc7e563df42614f398c4a10aabfe0ef983af8161c',
    'gg/vape/module/combat/automace/AutoMaceRotationController.java': '875eb671fd24c13467ac5a12f9d15eb5e53e9bf4a4d95eae1c021b5488fed1fc',
    'gg/vape/module/extension/ReloadScriptsModule.java': '77673c39df5d24ca32e9369d7e99b3ef926ea27528f52de19622b4f3fda57133',
    'gg/vape/module/none/TextGuiSettings.java': '571c88f99b19915b39fff4b28d865622aba6042ae2dc40a9dddc354236433b96',
    'gg/vape/module/render/Search.java': '05378e2f59303c2bb7407bc7a436b31e3f64fc05dfc800f868cb317508769df5',
    'gg/vape/module/render/SpawnerFinder.java': '073f19b1c83e9cd5afe1e1340279cbc6eb098a5d3c190540c9348138084c4dd0',
    'gg/vape/module/render/StorageESP.java': '2ca853d26f932c541969262e2dc648d2972de19e5b80a93d719d2b9cea5133e5',
    'gg/vape/module/render/hud/MotionBlur.java': 'af76ec0f265aeb8729c76ecad776dc5a32afa3ef4bf7d2ae1b72c7b4a1413fe3',
    'gg/vape/module/render/search/SearchBlockChunkScanner.java': '30bc10d3baa17c3dc17ffee4d958f933871b35a9fa29b716b641d54e62eee505',
    'gg/vape/module/render/spawnerfinder/SpawnerNameMatcher.java': '0ae96558d2a699b5773eb4708cc5ca79549ecb3dd862e696ea3404c6ec2187c2',
    'gg/vape/module/render/storage/StorageClusterFilter.java': '84b1d5f6befb2427b711634f98363d244e737b66e9a07dfbb7277d72ced5408b',
    'gg/vape/rotation/AdaptiveRotationController.java': 'c214a0358b332a0d27c7c41278d975af06e2b7d13b2c5ea48103f9a5f3bcc653',
    'gg/vape/rotation/MouseRotationController.java': '7b90c8a8a3b279ceb06a9b12989fbf0c6e6256ae8074c041e5939f962d887923',
    'gg/vape/rotation/RotationManager.java': '8d8f48842d2c93945b313a8136f504a0c8634c75aa4271d01bc0098e992eeff9',
    'gg/vape/rotation/RotationMath.java': 'ecec256ddc8bbd941725fc807f6a60e89d658dae4166433cdf893c0cd602b18b',
    'gg/vape/ui/unmap/SearchBlock.java': '93c5689ea772c35a587217e4f98fe4fb6803093bd66f2c8d4201a2a68df234f9',
    'gg/vape/utils/processors/SearchProcessor.java': '6874733825ed8a3af489af03c1a9a4a8ed18d970c6cadca92ec449dc5e80725f',
    'gg/vape/utils/processors/SearchResultIndex.java': '36034fa4b16747487a6e39773bef017cb483d165ed824149c7fc511e6d1df0c2',
    'gg/vape/utils/processors/SearchScanCursor.java': '2681d1941baa725b2601cfb49296e72c4d26797fa10c428e87ae99750fa4a13b',
    'gg/vape/utils/render/RenderUtil.java': 'bc01b189ca3b5210148a225635678a4315d1fde246addae826642d7d0b003ce5',
    'gg/vape/utils/render/WorldOverlayGlState.java': '6cda41432aec210105e9046ff95a004e174936ff93b88823c7a28eac8d8995c4',
}
JAVA_ADDITIONS = {
    'gg/vape/brand/SoulboundBrand.java',
    'gg/vape/brand/SoulboundPaths.java',
    'gg/vape/module/combat/automace/AimSelectionRules.java',
    'gg/vape/module/combat/automace/AutoMaceAttackGate.java',
    'gg/vape/module/render/spawnerfinder/SpawnerNameMatcher.java',
    'gg/vape/module/render/storage/StorageClusterFilter.java',
    'gg/vape/rotation/RotationMath.java',
    'gg/vape/utils/processors/SearchResultIndex.java',
    'gg/vape/utils/processors/SearchScanCursor.java',
    'gg/vape/utils/render/WorldOverlayGlState.java',
}
JAVA_ADDED_CLASSES = {
    'gg/vape/brand/SoulboundBrand.class',
    'gg/vape/brand/SoulboundPaths.class',
    'gg/vape/module/combat/AutoMace$AimSelection.class',
    'gg/vape/module/combat/automace/AimSelectionRules.class',
    'gg/vape/module/combat/automace/AutoMaceAttackGate.class',
    'gg/vape/module/render/SpawnerFinder$1.class',
    'gg/vape/module/render/SpawnerFinder$Target.class',
    'gg/vape/module/render/StorageESP$1.class',
    'gg/vape/module/render/StorageESP$Target.class',
    'gg/vape/module/render/spawnerfinder/SpawnerNameMatcher.class',
    'gg/vape/module/render/storage/StorageClusterFilter$1.class',
    'gg/vape/module/render/storage/StorageClusterFilter$Cell.class',
    'gg/vape/module/render/storage/StorageClusterFilter$Position.class',
    'gg/vape/module/render/storage/StorageClusterFilter.class',
    'gg/vape/rotation/RotationMath$StepQuantizer.class',
    'gg/vape/rotation/RotationMath$TargetPredictor.class',
    'gg/vape/rotation/RotationMath.class',
    'gg/vape/utils/processors/SearchResultIndex$1.class',
    'gg/vape/utils/processors/SearchResultIndex$Entry.class',
    'gg/vape/utils/processors/SearchResultIndex$Position.class',
    'gg/vape/utils/processors/SearchResultIndex.class',
    'gg/vape/utils/processors/SearchScanCursor.class',
    'gg/vape/utils/render/WorldOverlayGlState.class',
}
ENHANCED_MODULE_SOURCES = {
    name for name in APPROVED_ENHANCEMENTS
    if name.startswith("gg/vape/module/") and name not in JAVA_ADDITIONS
}
MODULE_EXCEPTIONS = {
    "gg/vape/module/none/ClientSettings.java",
}

# All other native files, including the reflective-loader implementation and
# bridge, must match the baseline. Reviewed translations and loader integrity
# changes are pinned by content; binary assets are compared byte-for-byte.
APPROVED_NATIVE = {
    'native/CMakeLists.txt': '224ce5d055b623ed50292583f7917b117d656b59870cdf409ade8e36448f131b',
    'native/brand_version.h.in': '27ce29c6ecbc889b517ee4279d730d0622394f87aa687eddef3afb8c444f4d16',
    'native/client_data_path.h': 'fe55c37ca5d19822447a9e9109c4f3882aad6d8498c824a9d5f8e46dfdaa4df4',
    'native/dllmain.c': 'a16b7b6a31ffd4450d98c8ca78a6379672d753744a1b388c8ab79ad84914e2c3',
    'native/injector_icon.rc': '650dafc60a90c7385561267c7024a505574c3f593a2ef1baef7d2933d5904a7c',
    'native/injector.c': '8f000797c4e9624a53e897a8cee8010b018af329363e124697d027ba1abc5277',
    'native/loader_gui/assets/logo.png': '1039296a4a77fb23c50d534020b0b87d4573b389732a286d6b19bc64c6e5cad2',
    'native/loader_gui/controller_model.cpp': 'd067357f5b30a4f50e465309ca4873b8ccc542e9f1c4ab0cf143fe5d5970c8df',
    'native/loader_gui/controller_model.h': '3beafc76870575c7776836987eca4b2aeded6769e2674aae8281ef58aee004f5',
    'native/loader_gui/controller_ui.cpp': 'f88c4e3d454c733562aaea70bd71d39d5cde73fc0cab846c709669922d2309e2',
    'native/loader_gui/embedded_dll_materializer.cpp': '51eb65cf18cfbd16bdeb1b3ba826b43c56f41d9d1053037498e0545e15f08b95',
    'native/loader_gui/embedded_dll_materializer.h': '61b6dbe7d36f68e7e4b454f984cd08ba3a369226ea650b21027f97e328f30d9e',
    'native/loader_gui/embedded_resource_integrity.h': 'f05b3c7e458e647768b15cade70e6ffb7e8cc03a582df6b046cd236e3ae80385',
    'native/loader_gui/icon.rc': '66badb4c8b29157ba69aeee17b07a3e5d39097ad1808868aeb2bd60b6f87c36e',
    'native/loader_gui/injection_coordinator.cpp': '55f5d1c838b94f63b6a490c3298dbe5ae12ca60c60190d42502f36ab9a083727',
    'native/loader_gui/loader_language.cpp': '2ab4680092e008f9f547e9960a0f0c738c9dda7631f0a812b51617322c9e823a',
    'native/loader_gui/loader_language.h': '06ec241e8626d994baeba1a5b54f723baa8fc7f27bb13b46ca5c0b0e73d8b2f7',
    'native/loader_gui/loader_strings.cpp': '9fd414ad43389c891a4197d201567975bf5749941e0029aeb47b9cdab36e9179',
    'native/loader_gui/loader_strings.h': '252956b897d714475779f7d246b47f526e3804f7b4d4706c154918954b7d1892',
    'native/loader_gui/locked_resource_file.h': '962de2b110e7b4350fef1caff4194d78eefff4fe3243c6c8c3a362575bd30583',
    'native/loader_gui/main.cpp': 'c0e53f00ea8ceadc1672a068c833cc7253a1efb302b53225e054621b6cfa9b86',
    'native/safe_file_materialization.c': 'db608a9b7fec00024ddf7d322e8842fab059bec5a28f3b18186dd1047c8f3bc0',
    'native/safe_file_materialization.h': 'bb9a84dda8d1a25afaae878ce1b2e605e13983d3c3297ae4d9cc3f5ea18bed7b',
    'native/soulbound.ico': '596337017d4513c0d51fedbc8eb8269a18b349fe564753b910a08432c6fc4ce5',
    'native/tests/client_data_path_test.c': '754be7351eb06ada365bb004a8d46139c1ba965569d4eabf8d7d67eef92f177c',
    'native/tests/embedded_dll_materializer_test.cpp': '70b94d81c8d19fdb90e4de50201d6cc7145f9dbd7d80df7746be1c0dd385bee7',
    'native/tests/embedded_resource_integrity_test.cpp': '614d4692e7cb85b1c4a712b88a3e9b0401627caa15e2e76b93c2ef04498bc2b8',
    'native/tests/loader_language_test.cpp': 'b21e9c690c90d9618ad0bc8b936b91fe6b67ef40cf40e7c8c22419fa4859d741',
    'native/tests/loader_strings_test.cpp': '0d60fd78fda307e493b703997203d92633194fa9d4d246f45481690cce048de0',
    'native/tests/locked_resource_file_test.cpp': '61da7589b3249cfa5d608773291de2ff42a8e1cd6bb9964d927929f6420dca5d',
    'native/tests/materializer_fixture.rc': 'e5039e8de87d856490b1bcd4c23f5011a604d090982b21713e0aec229ce9bbfd',
    'native/tests/safe_file_materialization_test.cpp': '2330079c724d0a50778f85e3916a203db485ae5b655dae2f6ac0b90840620383',
}
NATIVE_ADDITIONS = {
    'native/brand_version.h.in',
    'native/client_data_path.h',
    'native/loader_gui/embedded_dll_materializer.cpp',
    'native/loader_gui/embedded_dll_materializer.h',
    'native/loader_gui/embedded_resource_integrity.h',
    'native/loader_gui/loader_language.cpp',
    'native/loader_gui/loader_language.h',
    'native/loader_gui/locked_resource_file.h',
    'native/safe_file_materialization.c',
    'native/safe_file_materialization.h',
    'native/soulbound.ico',
    'native/tests/client_data_path_test.c',
    'native/tests/embedded_dll_materializer_test.cpp',
    'native/tests/embedded_resource_integrity_test.cpp',
    'native/tests/loader_language_test.cpp',
    'native/tests/locked_resource_file_test.cpp',
    'native/tests/materializer_fixture.rc',
    'native/tests/safe_file_materialization_test.cpp',
}
APPROVED_RESOURCES = {
    'src/main/resources/META-INF/soulbound-version.properties': '454c66d5e8b60d2c3be5e7b98d403a183f9927e4c00b78b21c0f0287adb3e2a0',
    'src/main/resources/resources/chinese.properties.txt': 'd384c1af085267bfe4da6d4044af93a3954163daf2bc011e5e31a7436c20932f',
    'src/main/resources/resources/english.properties.txt': 'a1f6b051ca3fb56365983d0a640caad81bb7a0fc5b43854d1cf58ec616cbe40e',
    'src/main/resources/resources/french.properties.txt': '7abddd8c3ffc485bd7dc1bf54d639b02648904040ebcb24161bfb98d38e6a59a',
    'src/main/resources/resources/textures/v4.png': 'e09d5ca3bc6fda431955156b9de2cb4469b52c0419c9663f4f9a724e68b356da',
    'src/main/resources/resources/textures/vapelogo.png': '100ad605a9cb9686cef38154c4b204dfc0bf811a5bd0f72b8e5205b172e1ac44',
}
NATIVE_DELETIONS = {
    'native/vape-v4.21.ico': 'cd857a1b00744675357b7fc4d889e7a0845920d2fae0111ece9b475e193df3a2',
}
RESOURCE_ADDITIONS = {
    'src/main/resources/META-INF/soulbound-version.properties',
}
TEXT_SUFFIXES = {
    ".c", ".cpp", ".h", ".cmake", ".in", ".rc", ".manifest", ".txt",
    ".json", ".csv", ".srg", ".map", ".frag", ".vert", ".properties",
}


class ParityError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ParityError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(text, old, new, description):
    require(text.count(old) == 1, "Expected exactly one " + description)
    return text.replace(old, new, 1)


def module_original_form(name, data):
    """Reverse just the documented module localization edits."""
    text = data.decode("utf-8")
    if name.endswith("/none/ClientSettings.java"):
        text = replace_once(
            text,
            '(String)"Language", (ModeSelection)FontSelector.j, (ModeSelection[])new ModeSelection[]{FontSelector.j, FontSelector.c}',
            '(String)"Language", (ModeSelection)FontSelector.c, (ModeSelection[])new ModeSelection[]{FontSelector.j, FontSelector.c}',
            "GUI Language default",
        )
    return text.encode("utf-8")


def file_map(directory, suffix):
    return {p.relative_to(directory).as_posix(): p for p in directory.rglob("*" + suffix) if p.is_file()}


def audit_sources(reference, current):
    ref_root = reference / "src/main/java"
    cur_root = current / "src/main/java"
    ref_files, cur_files = file_map(ref_root, ".java"), file_map(cur_root, ".java")
    require(ref_files and cur_files, "Java source trees are missing")
    require(not (ref_files.keys() & JAVA_ADDITIONS), "An expected addition exists in the baseline")
    require(ref_files.keys() | JAVA_ADDITIONS == cur_files.keys(), "Java source inventory changed")
    approved = {**APPROVED_SOURCES, **APPROVED_ENHANCEMENTS}
    require(not (set(APPROVED_SOURCES) & set(APPROVED_ENHANCEMENTS)), "Overlapping review categories")
    require(ENHANCED_MODULE_SOURCES <= set(APPROVED_ENHANCEMENTS), "Unreviewed module enhancement")
    changed = set()
    module_count = 0
    for name, cur_path in cur_files.items():
        # Windows checkouts may use CRLF; compare canonical source text.
        before = ref_files[name].read_bytes().replace(b"\r\n", b"\n") if name in ref_files else None
        after = cur_path.read_bytes().replace(b"\r\n", b"\n")
        if name.startswith("gg/vape/module/"):
            module_count += 1
            if name in MODULE_EXCEPTIONS:
                require(module_original_form(name, after) == before,
                        "Unapproved logic change in localized module source " + name)
            elif name not in ENHANCED_MODULE_SOURCES and name not in JAVA_ADDITIONS:
                require(before == after, "Unexpected module source change: " + name)
        if before != after:
            changed.add(name)
            require(name in approved, "Unexpected Java source change: " + name)
            require(sha256(after) == approved[name], "Unreviewed change in " + name)
    require(changed == set(approved), "The reviewed source-change set differs")
    require(module_count > 0, "No module sources found")
    return len(ref_files), module_count, len(changed)


def audit_tree(reference, current, directory, approved, additions, excluded_names=(), deletions=None):
    deletions = deletions or {}
    def collect(root):
        folder = root / directory
        require(folder.is_dir(), "Missing directory: " + str(folder))
        return {p.relative_to(root).as_posix(): p for p in folder.rglob("*")
                if p.is_file() and p.name not in excluded_names}

    before, after = collect(reference), collect(current)
    require(not (before.keys() & additions), directory + " addition already exists in baseline")
    require(set(deletions) <= before.keys(), directory + " deletion missing from baseline")
    require((before.keys() - deletions.keys()) | additions == after.keys(),
            directory + " inventory changed; missing=" + str(sorted(before.keys() - after.keys() - deletions.keys())[:12])
            + ", added=" + str(sorted(after.keys() - before.keys() - additions)[:12]))
    for name, digest in deletions.items():
        data = before[name].read_bytes()
        if before[name].suffix in TEXT_SUFFIXES:
            data = data.replace(b"\r\n", b"\n")
        require(sha256(data) == digest, "Retired baseline file differs: " + name)
    changes = set()
    for name, path in after.items():
        data = path.read_bytes()
        original = before[name].read_bytes() if name in before else None
        # Checkouts on Windows may convert text to CRLF. Do not alter binary assets.
        if path.suffix in TEXT_SUFFIXES or path.name == ".gitignore":
            data = data.replace(b"\r\n", b"\n")
            if original is not None:
                original = original.replace(b"\r\n", b"\n")
        if data != original:
            changes.add(name)
            require(name in approved, "Unreviewed change in " + name)
            require(sha256(data) == approved[name], "Unreviewed content in " + name)
    require(changes == set(approved), directory + " reviewed-change set differs")
    return len(before), len(changes) + len(deletions)


def verify_baseline_checkout(path):
    def git(*args):
        result = subprocess.run(["git", "-C", str(path.resolve()), *args],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                encoding="utf-8", errors="replace", check=False)
        require(result.returncode == 0, "Cannot verify baseline checkout: " + result.stderr.strip())
        return result.stdout.strip()

    require(git("rev-parse", "HEAD") == BASE_COMMIT, "Baseline checkout is not the pinned commit")
    status = git("status", "--porcelain", "--untracked-files=all", "--",
                 "src/main/java", "src/main/resources", "native")
    require(not status, "Baseline source or resources have local changes: " + status[:500])


def unpack(fmt, data, offset):
    size = struct.calcsize(fmt)
    require(offset >= 0 and offset + size <= len(data), "Truncated PE image")
    return struct.unpack_from(fmt, data, offset)


def pe_resource(image, resource_id):
    """Read one numeric RT_RCDATA resource from a PE32/PE32+ file safely."""
    require(image[:2] == b"MZ", "Missing DOS header")
    nt = unpack("<I", image, 0x3C)[0]
    require(image[nt:nt + 4] == b"PE\0\0", "Missing PE header")
    sections = unpack("<H", image, nt + 6)[0]
    optional_size = unpack("<H", image, nt + 20)[0]
    optional = nt + 24
    magic = unpack("<H", image, optional)[0]
    require(magic in (0x10B, 0x20B), "Unsupported PE optional header")
    data_directory = optional + (96 if magic == 0x10B else 112)
    require(data_directory + 24 <= optional + optional_size, "Missing resource directory")
    resource_rva, resource_size = unpack("<II", image, data_directory + 16)
    require(resource_rva and resource_size, "PE image has no resources")
    table = optional + optional_size
    section_ranges = []
    for index in range(sections):
        off = table + index * 40
        virtual_size, va, raw_size, raw_offset = unpack("<IIII", image, off + 8)
        section_ranges.append((va, virtual_size, raw_size, raw_offset))

    def offset_for(rva, size):
        for va, _virtual_size, raw_size, raw_offset in section_ranges:
            if va <= rva and rva - va + size <= raw_size:
                result = raw_offset + rva - va
                require(result + size <= len(image), "PE resource exceeds file")
                return result
        raise ParityError("PE resource RVA is outside file-backed sections")

    def entries(relative):
        off = offset_for(resource_rva + relative, 16)
        named, numbered = unpack("<HH", image, off + 12)
        count = named + numbered
        require(count <= 65535, "Too many PE resource entries")
        offset_for(resource_rva + relative, 16 + count * 8)
        return [unpack("<II", image, off + 16 + i * 8) for i in range(count)]

    def numeric_entry(relative, desired):
        matches = [entry for entry in entries(relative)
                   if not entry[0] & 0x80000000 and entry[0] == desired]
        require(len(matches) == 1, "Missing or duplicate PE resource " + str(desired))
        return matches[0][1]

    type_dir = numeric_entry(0, 10)  # RT_RCDATA
    require(type_dir & 0x80000000, "Invalid PE resource type directory")
    name_dir = numeric_entry(type_dir & 0x7FFFFFFF, resource_id)
    require(name_dir & 0x80000000, "Invalid PE resource name directory")
    languages = entries(name_dir & 0x7FFFFFFF)
    require(languages, "PE resource has no language")
    data_rel = languages[0][1]
    require(not data_rel & 0x80000000, "Unexpected PE resource directory nesting")
    data_entry = offset_for(resource_rva + data_rel, 16)
    data_rva, size = unpack("<II", image, data_entry)
    require(data_rva and size, "Empty PE resource")
    data_offset = offset_for(data_rva, size)
    return image[data_offset:data_offset + size]


def zip_hashes(data, predicate):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [name for name in archive.namelist() if predicate(name)]
        require(len(names) == len(set(names)), "Duplicate entries in JAR")
        return {name: sha256(archive.read(name)) for name in names}


def same_inventory(left, right, description):
    missing = sorted(left.keys() - right.keys())
    added = sorted(right.keys() - left.keys())
    require(not missing and not added,
            description + " inventory changed; missing=" + str(missing[:12]) + ", added=" + str(added[:12]))


def module_class(name):
    return name.startswith("gg/vape/module/") and name.endswith(".class")


def own_class(name):
    return (name.startswith("gg/vape/") or name.startswith("func/")) and name.endswith(".class")


def approved_class(name):
    for source in set(APPROVED_SOURCES) | set(APPROVED_ENHANCEMENTS):
        stem = source[:-5]
        if name == stem + ".class" or name.startswith(stem + "$"):
            return True
    return False


APPROVED_PRODUCT_RESOURCE_CHANGES = {
    'resources/textures/v4.png': 'e09d5ca3bc6fda431955156b9de2cb4469b52c0419c9663f4f9a724e68b356da',
    'resources/textures/vapelogo.png': '100ad605a9cb9686cef38154c4b204dfc0bf811a5bd0f72b8e5205b172e1ac44',
}


def product_resource(name):
    return (not name.endswith("/")
            and name.startswith(("mappings/", "resources/", "liquidbounce/"))
            and name not in {"resources/english.properties.txt", "resources/chinese.properties.txt",
                             "resources/french.properties.txt"})


def audit_embedded_resources(current_jar, embedded_jar):
    nonclass = lambda name: not name.endswith("/") and not name.endswith(".class")
    current = zip_hashes(current_jar, nonclass)
    embedded = zip_hashes(embedded_jar, nonclass)
    same_inventory(current, embedded, "Embedded non-class resource")
    changes = sorted(name for name in current if current[name] != embedded[name])
    require(not changes, "Obfuscation changed embedded resources: " + str(changes[:20]))
    return len(current)


def inspect_unrecognized_release_layout(data, ref_modules):
    """Describe an original JAR whose module names do not match our build.

    Referenced names are diagnostic strings, not proof the corresponding class
    or behavior is present. In particular, an obfuscator may retain old names
    in constant pools even when it changes the actual class entry paths.
    """
    expected = {name[:-6] for name in ref_modules}
    references = set()
    roots = Counter()
    example = None
    pattern = re.compile(rb"gg/vape/module/[A-Za-z0-9_$/]+")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [entry for entry in archive.namelist() if entry.endswith(".class")]
        require(names and len(names) == len(set(names)), "Original JAR has no classes or duplicate classes")
        for name in names:
            roots[name.split("/", 1)[0]] += 1
            raw = archive.read(name)
            found = {match.decode("ascii") for match in pattern.findall(raw)} & expected
            if found and example is None:
                example = name + " references " + ", ".join(sorted(found)[:3])
            references.update(found)
    return {
        "classes": len(names),
        "roots": ", ".join(f"{name}: {count}" for name, count in roots.most_common(6)),
        "referenced_module_names": len(references),
        "example": example,
    }


def audit_jars(reference_jar, english_jar, release_jar):
    ref_modules = zip_hashes(reference_jar, module_class)
    eng_modules = zip_hashes(english_jar, module_class)
    release_modules = zip_hashes(release_jar, module_class)
    require(ref_modules, "Baseline module class inventory missing")
    require(eng_modules, "English module class inventory missing")
    added_modules = {name for name in JAVA_ADDED_CLASSES if module_class(name)}
    require(not (ref_modules.keys() & added_modules), "An expected added module class exists in the baseline")
    require(ref_modules.keys() | added_modules == eng_modules.keys(),
            "English module class inventory changed; missing=" + str(sorted(ref_modules.keys() - eng_modules.keys())[:12])
            + ", added=" + str(sorted(eng_modules.keys() - ref_modules.keys() - added_modules)[:12]))
    release_layout = None
    if release_modules:
        same_inventory(release_modules, ref_modules, "Original release module class")
    else:
        release_layout = inspect_unrecognized_release_layout(release_jar, ref_modules)
    module_changes = sorted(name for name in ref_modules if ref_modules[name] != eng_modules[name])
    required_modules = {name[:-5] + ".class" for name in MODULE_EXCEPTIONS | ENHANCED_MODULE_SOURCES}
    allowed_modules = {name for name in ref_modules if approved_class(name)}
    require(required_modules <= set(module_changes) <= allowed_modules,
            "Unexpected module class changes: " + str(module_changes))

    ref_classes = zip_hashes(reference_jar, own_class)
    eng_classes = zip_hashes(english_jar, own_class)
    require(not (ref_classes.keys() & JAVA_ADDED_CLASSES), "An expected added class exists in the baseline")
    require(ref_classes.keys() | JAVA_ADDED_CLASSES == eng_classes.keys(),
            "Owned class inventory changed; missing=" + str(sorted(ref_classes.keys() - eng_classes.keys())[:12])
            + ", added=" + str(sorted(eng_classes.keys() - ref_classes.keys() - JAVA_ADDED_CLASSES)[:12]))
    extra = sorted(name for name in ref_classes
                   if ref_classes[name] != eng_classes[name] and not approved_class(name))
    require(not extra, "Unexpected changed supporting classes: " + str(extra[:20]))

    ref_resources = zip_hashes(reference_jar, product_resource)
    eng_resources = zip_hashes(english_jar, product_resource)
    same_inventory(ref_resources, eng_resources, "Product resource")
    resource_changes = {name for name in ref_resources if ref_resources[name] != eng_resources[name]}
    require(resource_changes == set(APPROVED_PRODUCT_RESOURCE_CHANGES),
            "Unexpected or missing reviewed resource changes: " + str(sorted(resource_changes)))
    for name, digest in APPROVED_PRODUCT_RESOURCE_CHANGES.items():
        require(eng_resources[name] == digest, "Unreviewed compiled resource: " + name)

    release_differences = (sorted(name for name in release_modules
                                  if release_modules[name] != ref_modules[name])
                           if release_modules else None)
    return {
        "module_classes": len(ref_modules),
        "matching_module_classes": len(ref_modules) - len(module_changes),
        "module_changes": module_changes,
        "own_classes": len(ref_classes),
        "resources": len(ref_resources),
        "release_differences": release_differences,
        "release_english_matches": (sum(release_modules[name] == eng_modules[name] for name in release_modules)
                                    if release_modules else None),
        "release_layout": release_layout,
    }


def emit_notice(title, text):
    print("::notice title=" + title + "::" + text.replace("%", "%25").replace("\n", "%0A"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-source", type=Path, required=True)
    parser.add_argument("--current-source", type=Path, required=True)
    parser.add_argument("--baseline-jar", type=Path, required=True)
    parser.add_argument("--current-jar", type=Path, required=True)
    parser.add_argument("--embedded-jar", type=Path,
                        help="Expected embedded JAR (may be the randomized current build)")
    parser.add_argument("--original-exe", type=Path, required=True)
    parser.add_argument("--original-jar-output", type=Path,
                        help="Optional temporary output for further offline inspection; do not distribute")
    parser.add_argument("--current-exe", type=Path, required=True)
    parser.add_argument("--current-dll", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    verify_baseline_checkout(args.baseline_source)
    source_count, module_source_count, changed_sources = audit_sources(args.baseline_source, args.current_source)
    native_count, native_changes = audit_tree(args.baseline_source, args.current_source,
                                              "native", APPROVED_NATIVE, NATIVE_ADDITIONS,
                                              ("README.md", "README_EN.md"), NATIVE_DELETIONS)
    resource_count, resource_changes = audit_tree(args.baseline_source, args.current_source,
                                                  "src/main/resources", APPROVED_RESOURCES, RESOURCE_ADDITIONS)
    original = args.original_exe.read_bytes()
    require(sha256(original) == ORIGINAL_SHA256, "Original EXE SHA-256 does not match the supplied hash")
    release_dll = pe_resource(original, 422)
    release_jar = pe_resource(release_dll, 421)
    if args.original_jar_output:
        args.original_jar_output.parent.mkdir(parents=True, exist_ok=True)
        args.original_jar_output.write_bytes(release_jar)

    current_jar = args.current_jar.read_bytes()
    embedded_jar = args.embedded_jar.read_bytes() if args.embedded_jar else current_jar
    current_exe = args.current_exe.read_bytes()
    embedded_dll = pe_resource(current_exe, 422)
    require(embedded_dll == args.current_dll.read_bytes(), "External DLL differs from embedded DLL")
    require(pe_resource(embedded_dll, 421) == embedded_jar, "EXE does not embed the expected Java payload")
    embedded_resource_count = audit_embedded_resources(current_jar, embedded_jar)
    stats = audit_jars(args.baseline_jar.read_bytes(), current_jar, release_jar)

    lines = [
        "Soulbound build: module parity audit against the recovered 4.21.58 baseline",
        "",
        "Baseline source commit: " + BASE_COMMIT,
        "Original release EXE SHA-256 verified: " + ORIGINAL_SHA256,
        "English EXE SHA-256: " + sha256(current_exe),
        "English DLL SHA-256: " + sha256(embedded_dll),
        "English source-build Java payload SHA-256: " + sha256(current_jar),
        "English embedded Java payload SHA-256: " + sha256(embedded_jar),
        f"Java baseline sources: {source_count}; module sources: {module_source_count}; reviewed changes/additions: {changed_sources}.",
        f"Module sources: {len(MODULE_EXCEPTIONS)} reviewed localization edits and {len(ENHANCED_MODULE_SOURCES)} reviewed behavioral changes, plus pinned additions; all others match baseline.",
        "The reversible localization edit and other reviewed changes are pinned by source hashes.",
        "Behavioral improvements intentionally differ from the baseline; they require runtime testing.",
        f"Native files: {native_count} baseline files; {native_changes} reviewed localization/integrity/build/test changes or additions.",
        f"Resource files: {resource_count} baseline files; {resource_changes} reviewed translation, artwork or version changes.",
        f"Same-toolchain builds: {stats['matching_module_classes']}/{stats['module_classes']} module class files are byte-identical.",
        "Differing baseline module classes (reviewed localization and behavioral changes):",
        *("  " + name for name in stats["module_changes"]),
        f"Owned class inventory: {stats['own_classes']} baseline files plus {len(JAVA_ADDED_CLASSES)} reviewed additions; no unreviewed class differences.",
        f"Non-language product resources ({stats['resources']} files) unchanged.",
        f"Embedded payload has the same {embedded_resource_count} non-class resources as the English source build.",
        "Native loading changes are pinned above; the EXE and optional DLL embed the expected JAR. This audit does not establish safety.",
    ]
    if stats["release_layout"] is None:
        official_matches = stats["module_classes"] - len(stats["release_differences"])
        lines += [
            f"Original release vs same-source baseline: {official_matches}/{stats['module_classes']} module class files byte-identical.",
            f"Original release vs English build: {stats['release_english_matches']}/{stats['module_classes']} module class files byte-identical.",
        ]
        if stats["release_differences"]:
            lines += ["Original-release differences (possibly compiler/build differences; not runtime-tested):"]
            lines += ["  " + name for name in stats["release_differences"]]
    else:
        layout = stats["release_layout"]
        lines += [
            "Original release path-based module comparison: INCONCLUSIVE (class paths differ).",
            f"Original JAR has {layout['classes']} classes; most frequent package roots: {layout['roots']}.",
            f"Its class data references {layout['referenced_module_names']} baseline module-class names;",
            "references are not proof of class inventory or equivalent behavior.",
        ]
        if layout["example"]:
            lines.append("Original class data example: " + layout["example"])
    lines += [
        "",
        "Scope: static source, class and resource checks only. Module behavior has not been",
        "tested inside Minecraft; this report is not a guarantee of runtime equivalence.",
        "",
    ]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines), encoding="utf-8")
    for line in lines:
        print(line)
    emit_notice("Module source review", f"{module_source_count} module source files checked; {len(MODULE_EXCEPTIONS)} localization edits and {len(ENHANCED_MODULE_SOURCES)} reviewed behavioral changes, plus pinned additions.")
    emit_notice("Module class comparison", f"{stats['matching_module_classes']}/{stats['module_classes']} module class files byte-identical in same-toolchain builds; reviewed enhancements intentionally differ.")
    if stats["release_layout"] is None:
        emit_notice("Original release comparison", f"Original EXE hash verified; {official_matches}/{stats['module_classes']} module class files match the baseline build, and {stats['release_english_matches']}/{stats['module_classes']} match the English build byte-for-byte.")
    else:
        layout = stats["release_layout"]
        message = (f"Original EXE hash verified; path-based comparison requires class-name normalization: "
                   f"{layout['classes']} class files, roots {layout['roots']}, "
                   f"{layout['referenced_module_names']} baseline module names referenced in class data.")
        emit_notice("Original release layout", message)
        if layout["example"]:
            emit_notice("Original class layout example", layout["example"])
    emit_notice("Runtime test limitation", "Static parity checks passed; module behavior was not tested in Minecraft.")


if __name__ == "__main__":
    try:
        main()
    except (ParityError, OSError, zipfile.BadZipFile, UnicodeError) as error:
        print("PARITY CHECK FAILED: " + str(error), file=sys.stderr)
        print("::error title=Module parity check::" + str(error).replace("%", "%25").replace("\n", "%0A"))
        sys.exit(1)
