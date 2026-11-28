#!/usr/bin/env python3
"""Audit the InevitableClient build against the pinned v4.21.58 source and EXE.

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

# Reviewed InevitableClient presentation and compatibility changes.
# Pin their complete contents so an unrelated change cannot hide inside an
# approved file. Deliberate future changes require a fresh parity review.
APPROVED_SOURCES = {
    'gg/vape/Vape.java': '8b20ca96d06d50ce6b1f35fc9a489b343ff1e84e448de69027ca149b675cb1c8',
    'gg/vape/config/ClientSettings.java': '952394203b5bbf400c33696608cd7028e6ec6c148af174b02a9e430d95ead701',
    'gg/vape/config/LocalConfigStore.java': '3367088d25e13d7ae5210b9e2218d2c7e4ae074a59c30ff8e09f49a959420245',
    'gg/vape/config/PublicProfileSettings.java': 'ad84092706c242e2ec7f02a7a8855a35f11cb25f95cf1e584c317e051e430b3b',
    'gg/vape/deeplearn/DeepLearningEngine.java': '1f64241b3890d23bc2af56745ca70d551f6a3da906173bd7c520e0172ca20309',
    'gg/vape/deeplearn/ModelManager.java': 'c560128cf229a70d79b094948653e6e8f554162729d63ea46b52297e071f997e',
    'gg/vape/deeplearn/models/MlpModel.java': '8cd6d37060266e275a3e0713991d0efbde06223439aeb71b54c5c88a5b9d2474',
    'gg/vape/event/impl/EventRenderWorldPassExecutorDrain.java': '64f504db1a0f4d4d366edeae82e05c08de59ef1d29802e1a0cb92d380b3f2991',
    'gg/vape/friend/ui/FriendRequestsPanel.java': '5c7cb4f6cc946c8784b890e039962fa3d1477834435ae08b2c171d89c198eed9',
    'gg/vape/friend/ui/OnlineFriendsFrame.java': '5ab0b2357e2b3770c8e23fd92b2593d97073ddd15f73556cee29ba86a1a86de7',
    'gg/vape/friend/ui/PartyMemberEntryComponent.java': 'a377edfc155ff2a9ffa9d446276b10f27b117f9dd08e8fdddbfac36e079eb8bd',
    'gg/vape/manager/client/GlobalSettingsController.java': '8ab4935e5b7953c4b6f447532c723c3e422d46443409ba1d5055ad29dce949d5',
    'gg/vape/manager/client/OnlineConnectionManager.java': 'eb181032d0a35d5c20fd44fae86ec71ca34f195def0150dec39d14fea5144cc4',
    'gg/vape/manager/client/OnlineSettings.java': 'af6318641bfe2af5cb97304f25e24ceb58d9b6c589ada61f1d0184ecba07ad68',
    'gg/vape/mapping/JavassistMappingTask.java': 'dc86afb17202af0dd75004739f6e77391fff9e69247ae0bdacdff8104bae1d06',
    'gg/vape/notification/NotificationSoundPlayer.java': '7da78b2b84033e44828797e091eb1a27e90d9d9c3648b9b5d61c24326d73e09c',
    'gg/vape/runtime/NativeBridge.java': 'ca5f8024b13b55a1143090742859dc7605d94962f8eae74fb78c35939c0b23de',
    'gg/vape/service/VapeServiceApplication.java': 'd8e7531cbe4e83cb6ecf48b00825bbaf1cc403a1d920f4e95e8f218beff5ab39',
    'gg/vape/service/VapeServiceLauncher.java': '6b2b6a667e1046a9b75b3339967951ec23a19451d6457c248a54082da7b0b3c1',
    'gg/vape/service/http/LegacyHttpServer.java': '5e024bda6742cef48aef842d7371a32eda82196abd3ac86f977d8abb7149ccd0',
    'gg/vape/sync/SyncThread.java': '5ae3a1243cf2dc3608e8617e5e23c601499ae4c8a69d0b82308bfbcf7e647579',
    'gg/vape/tutorial/TutorialWelcomePanel.java': 'f5c318bbba116ce700393b82b3c1a5dae75e8132a01fa7d07d8d7688cac124e3',
    'gg/vape/tutorial/page/HudTutorialPage.java': '63aa7878b6d12af68447822d6d80e320c7ac40c30df18f0a747c29779d343310',
    'gg/vape/tutorial/page/ModulesTutorialPage.java': 'd1b698ef4c848f5ebdb2d7d7bf899ceda131b1679bf712cbdfed4fef5da46aa8',
    'gg/vape/ui/click/frame/impl/ClientSettingsComponentFactory.java': '069d09b5fddd345a0cbe787388fd3be8627c8c96b369d1a2960f06dab4913dba',
    'gg/vape/ui/click/frame/impl/ClientSettingsFrame.java': '9f981a9d5329720e72610d403001af2f85048aa42a42a3d76fc84c81efb13e10',
    'gg/vape/ui/click/frame/impl/ClientSettingsSearchFrameHeader.java': 'f408a22ab098bd8f0c7563fba2754934cd5b6bf7f9a20398188be80ef8b8a4fc',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsFriendCardFactory.java': '43ab6c2d76fe11f26409d68fd05efb3f68dfbe9bcb6e1c14d985da503228d2f3',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsPage.java': '25c0a52583ae608f55459ef0ad1af392326e46d976cea9cc2bc90bc1c96985d0',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFriendsThemeConfigFactory.java': '4ba31a446211ecf0bcd58611e736651ef3936ba5331a7339fd88f039863cdd49',
    'gg/vape/ui/click/frame/impl/main/ClickGuiMainFrameHeaderActionComponent.java': 'bd03b966196cc15b1afd7df6a5c8ba2239a8916fc7f0d9676d0dbcb7bf70003c',
    'gg/vape/ui/click/frame/impl/main/ClickGuiOverlaySpecFactoryA.java': '7dab75785fc4539832561f43c06bc96b35c4952ebf8500d06cca21818c217323',
    'gg/vape/ui/click/frame/impl/online/OnlineAccountLinkCodePageComponent.java': 'fe5b59474931ed35a509fec59138da591947e96cfa55df186d0d9b6ac6453fb6',
    'gg/vape/ui/click/frame/impl/online/OnlineAccountUnavailablePageComponent.java': 'e9a4017b5f0d7d53f9583804fe937c20bfcbbf77ddcb3ae8841e9568f9ad4c68',
    'gg/vape/ui/click/frame/impl/online/OnlineConnectionRetryPageComponent.java': 'c6972148235f2731fe093abfaf8edf685c64bee18f4c4bf335ad9da77815b548',
    'gg/vape/ui/click/frame/impl/online/OnlineConnectionSettingsFrame.java': '95bd3bb4068dd8abba09c6e711f46f283fa3be26902aabfded12967586cedb57',
    'gg/vape/ui/theme/ThemeColors.java': '55fa65915668a91d913f185387f5828c7682ab610268c1aee3fc166baaecb97a',
    'gg/vape/utils/network/AnticheatProfile.java': '1954e7755d9e936b2264b93a22836eeadf7c678f584510ba292e2c72d626bd8d',
    'gg/vape/utils/network/OutboundPacketPipeline.java': '1b991f5cd372546b2412b71cca30f8985834fa35fddd71e902ff04d0074be5b7',
    'gg/vape/utils/render/OpenGlDeviceInfo.java': 'abeb2fc96b3d007dad7405d79d17722213f7574fde98dc52a6e705eea04fdcfe',
    'gg/vape/utils/render/RemoteImageHttpDownloader.java': '2b702fa731df3ee54a02ee35238b643522e6986c620e250757d2ffc39ab83c62',
    'gg/vape/utils/render/RenderBatchManager.java': '645b1e34c96cf27758314f80cde946fa855e67c5f04abcfac94bdce02432be3b',
}

# Reviewed behavioral improvements, module branding edits, and additions.
# Complete content hashes remain mandatory.
APPROVED_ENHANCEMENTS = {
    'gg/vape/api/PublicProfilePartialJsonPayloadBuilder.java': 'a3857df41f87f6e2e82bc87563a44375fb897b65c8d6b42455d9ddcc8438a2ea',
    'gg/vape/brand/InevitableBrand.java': '874d0d95b51970ba647609192bf4db7c84ed177974717555e4970919cce8fe44',
    'gg/vape/brand/InevitablePaths.java': '23f2abc77ff81fd11c72a592a874c32151457451daf0a9cc3f1a0bbc38d9434b',
    'gg/vape/config/ConfigJsonUtils.java': '68a6d60eb5be048c8b4e93d19859f6b4fdf541df1ae7df45a79924b755bd857f',
    'gg/vape/config/Profile.java': 'e7c3bb51a37bbbe67b23ab3eb38a21d37cc943f05375f465f822c4261b8b5c21',
    'gg/vape/config/ProfileDataRetention.java': '39fa81d74a4dc815564bf55a29e6975d57bd55eb48590856f11fbfcb1d6a3f10',
    'gg/vape/config/ProfileModuleSnapshot.java': 'aa746429d4bb6c46d0d1cb7f97a6f477537cd3ad35b159ab3589f9eb8d6ce66c',
    'gg/vape/config/ProfileSnapshot.java': 'a7183b6307116f4651bc385c60a87aa3767657e69f16520861cb565249991f55',
    'gg/vape/input/AttackKeyController.java': '38d5baeefc4cb441fde2c9823628f53ae031a3f46b01a179db1caf1fb2f31d8c',
    'gg/vape/input/BindCaptureTask.java': 'fd3a313158d0f57ab9242d27c934747399171e87cb03e4ad7272f3b920c05448',
    'gg/vape/input/BindCaptureThread.java': '4b051bad018cb68f2e479c5d8260d1eef3039c7d00e2429f4c593b5e9dd852ee',
    'gg/vape/manager/ExtensionManager.java': '5857e34e02e964c81b9b7bb73a12a98dedeacf4613c50fdeb42b53edef06b5e2',
    'gg/vape/manager/ModManager.java': '3ee9e2b4f190d716973f1cefa2c928f70cd61f6f1ab324a5cb4891b805078081',
    'gg/vape/manager/client/ProfilesManager.java': '4b1d980621694014b1a30bd9771327d93a16177d6b0e7d74bf04831e0ca90f27',
    'gg/vape/module/Macro.java': '0250ef85437afe789fa38d81b3ac8c065ee2ba793ddb3d271ad3f4e0966f2a72',
    'gg/vape/module/Mod.java': '79a368b271d15f5f69ad73314e34643ef7cf1e61d610aa19bc20703cba67cd71',
    'gg/vape/module/blatant/antibot/AntiBotBooleanValue.java': '5ab4ce05ca96c352200166646c086b3a2f8e7e8506b3c9674952e87dbbb06a46',
    'gg/vape/module/blatant/antibot/AntiBotModeValue.java': 'b5bc1a11e19c879869869afd449ff52db20e7f30142c6ce48ccfadccd601f8e3',
    'gg/vape/module/combat/AimAssist.java': '2a81e9bdfecf9a363cf4414ea138b658e203e234fd216c9ec016cd5a0d22ee35',
    'gg/vape/module/combat/AutoMace.java': 'f8b0956f993952eb7fd93ae6a52a9e52905c8a370e76d19ec7ccb3d75c45319f',
    'gg/vape/module/combat/HitSwap.java': 'c760686e00f000379f849964ad5fa718d776bfd55f4732c787692a4a32c5e922',
    'gg/vape/module/combat/SilentAura.java': '9bf061d1b83bb82ab47cef324a7c891072fecd348a5fefb3251f994516843662',
    'gg/vape/module/combat/Triggerbot.java': '6be6b8b2590fdf29a2a3a8eb08fd2d5fbc8f819abee3e0c1601ab244e87240c7',
    'gg/vape/module/combat/aimassist/AimAssistKissSubModule.java': '31ff65af290fb0af673f23383a362eb9e71321dfd30be131456560565062247c',
    'gg/vape/module/combat/aimassist/AimAssistRotationSubModule.java': 'a9b4a0bb9392facc5029d165d9f8936981012609079aa52f4c57570c7e6f1686',
    'gg/vape/module/combat/aimassist/AimAssistTargetingSubModule.java': '8bebb812eece35f0fe667ff4b8f6f3de429c4751c6fb4179684c08cedc4a8a0c',
    'gg/vape/module/combat/automace/AimSelectionRules.java': '8b25748d4dd7a2db2697c6397f86c428f7ee8c1f38aa202996f2674e480b946d',
    'gg/vape/module/combat/automace/AutoMaceAttackGate.java': '6384c92e5d2339f8122c917dc7e563df42614f398c4a10aabfe0ef983af8161c',
    'gg/vape/module/combat/automace/AutoMaceRotationController.java': '875eb671fd24c13467ac5a12f9d15eb5e53e9bf4a4d95eae1c021b5488fed1fc',
    'gg/vape/module/extension/ReloadScriptsModule.java': '77673c39df5d24ca32e9369d7e99b3ef926ea27528f52de19622b4f3fda57133',
    'gg/vape/module/extension/ScriptModule.java': 'd425360c738a98d698cf463948fb46a8fe0e60e42d300064e5bd2d9ebb50dde5',
    'gg/vape/module/none/ClientSettings.java': '5350edb8c10b08c3c2692609bd3439d73924c9d5d883df09b3f3688442474aba',
    'gg/vape/module/none/ClientSettingsBooleanValue.java': '2fd453fd444d15129e6136db3e17fcb4f18efc6738d0a77684f71be6c6be52d5',
    'gg/vape/module/none/TextGuiSettings.java': '8c33785f12c438de2ac8d8da7a17450172b8ea0b8706d304e2f36c54e0d4b50a',
    'gg/vape/module/render/Search.java': '05378e2f59303c2bb7407bc7a436b31e3f64fc05dfc800f868cb317508769df5',
    'gg/vape/module/render/SpawnerFinder.java': '073f19b1c83e9cd5afe1e1340279cbc6eb098a5d3c190540c9348138084c4dd0',
    'gg/vape/module/render/StorageESP.java': '2ca853d26f932c541969262e2dc648d2972de19e5b80a93d719d2b9cea5133e5',
    'gg/vape/module/render/hud/MotionBlur.java': 'ff76fa0206fbf96ba88e7c5269d82de548d32c43185617403bc3413c33ef4271',
    'gg/vape/module/render/search/SearchBlockChunkScanner.java': '30bc10d3baa17c3dc17ffee4d958f933871b35a9fa29b716b641d54e62eee505',
    'gg/vape/module/render/spawnerfinder/SpawnerNameMatcher.java': '0ae96558d2a699b5773eb4708cc5ca79549ecb3dd862e696ea3404c6ec2187c2',
    'gg/vape/module/render/storage/StorageClusterFilter.java': '84b1d5f6befb2427b711634f98363d244e737b66e9a07dfbb7277d72ced5408b',
    'gg/vape/module/utility/inventory/HotbarSlotRuleEditorComponent.java': '720ed9f63bc002441ebe151510f39495fe22bb2dc75f8b2bdac75d6fd8bd22b4',
    'gg/vape/module/utility/inventory/HotbarSlotRuleGroupComponent.java': 'f9ba4b12e2710fce644337d97a25233c0bca2c3a56ed5abb3cf9dea73b6fa845',
    'gg/vape/module/utility/inventory/HotbarSlotRuleGroupSelectClickHandler.java': 'a1c98138983cc123f42cf399cbf9bfbb3617888684ff89de51e4f57e54ada54b',
    'gg/vape/module/utility/inventory/HotbarSlotRuleItemPickerFrame.java': '9cce5797fe72741ddba9fe99e5eeefbb4571d4b5751bf2193dece999cf6da325',
    'gg/vape/module/utility/inventory/HotbarSlotRuleItemSelectClickHandler.java': '9a36ad9cbdef55e183f70e071228c1668cf6932391c3d9788385d5923a11a3e0',
    'gg/vape/module/utility/inventory/HotbarSlotRuleSlotSelectClickHandler.java': '64aa2d815e0d0c18e94e3118e58a46692705d169426611e8f9631d2e50dff6ed',
    'gg/vape/module/utility/inventory/HotbarSlotRuleValue.java': '2d0a852bd760da2d1882a13ef940f86ad8f7e015cc6c98419aadf137a2dad4c4',
    'gg/vape/module/utility/inventory/cleaner/AbstractInventoryFilterRule.java': '568f9e7d38f467d76382aa5656864e5af9e260b3907b49f53fbb477a90932b7c',
    'gg/vape/module/utility/inventory/cleaner/InventoryCleanerProfile.java': '15fa7318e93767ad704830f213bdc141354be4b9c29e10af143e4fd32e2b0051',
    'gg/vape/module/utility/inventory/cleaner/InventoryCleanerProfileValue.java': 'e8019d60c066874922063411a34846c03ac8d17c5fd5a9a15a5fb292ef96ee3f',
    'gg/vape/module/utility/inventory/cleaner/ui/InventoryCleanerPopupFrame.java': '6e2e60bac0b0e635c3cde15ee1d1e840650a41210e4b4fb8b494328a0b1942f6',
    'gg/vape/module/utility/inventory/cleaner/ui/InventoryCleanerProfileRowClickHandler.java': '83d32a622ceb5e347d7319341b7b418350486525be23dd5f6b3fa69098071e96',
    'gg/vape/module/utility/inventory/cleaner/ui/InventoryCleanerProfileValueComponent.java': 'c33a9f007ebefcf3204fc8281f6cd544379a6a2f6339c317019eea09680e955f',
    'gg/vape/module/utility/inventory/cleaner/ui/InventoryFilterRuleEditorFrame.java': 'b217ff2a82efeb58e5a775b78fcdadf8fb9f3bdfbd5146ed31912c96d7491394',
    'gg/vape/module/utility/inventory/cleaner/ui/InventoryFilterRuleEditorPanel.java': '7a0943dfab838305ea1fd48863ac94d3c6594cdd0828b15b5cd5ad30bca5d6dd',
    'gg/vape/rotation/AdaptiveRotationController.java': 'c214a0358b332a0d27c7c41278d975af06e2b7d13b2c5ea48103f9a5f3bcc653',
    'gg/vape/rotation/MouseRotationController.java': '7b90c8a8a3b279ceb06a9b12989fbf0c6e6256ae8074c041e5939f962d887923',
    'gg/vape/rotation/RotationManager.java': '8d8f48842d2c93945b313a8136f504a0c8634c75aa4271d01bc0098e992eeff9',
    'gg/vape/rotation/RotationMath.java': 'ecec256ddc8bbd941725fc807f6a60e89d658dae4166433cdf893c0cd602b18b',
    'gg/vape/script/ScriptRuntimeHost.java': '07d9fc1717e61695ed20c063b5eb589e12b3e12f2aea828301ccca395ad6ed5f',
    'gg/vape/service/store/AccountRecord.java': '5394d929facf5fb8d6516933f7e78acc6d69a20f3c1796638c4f51e485b18d6b',
    'gg/vape/service/store/FileStore.java': 'abbaa5c885c8ead39f30a8dbd4afc3737ced8eff4c58451f3b11670329ef738e',
    'gg/vape/service/store/ServiceState.java': '6050e217830d755c07f831887921117baccbf6e930768450da4534aeadd1f15e',
    'gg/vape/sync/RemoteProfileData.java': '407be35182ff2e3968460ffe8c9b169d0401d470c817b8f63a41d495a4001975',
    'gg/vape/sync/SyncDebounceWorker.java': 'eedf87484cf41a142f20b7244f19127faffd1f325d54df7725f75d01f8ba58dc',
    'gg/vape/sync/SyncStoreRequestWorker.java': '69a33176d5250f96cd956fab6cdd143fce0cb63475072ea02ac358a8abd80651',
    'gg/vape/ui/click/animation/ThemeColorAnimation.java': 'd804c3d1337275dc209c0f31f3c5396d2af147db7730b42f348db721c80813dd',
    'gg/vape/ui/click/component/DropdownSelectComponent.java': '544dbbd0c4b7413fce8f3ec31083ab856a17301371a0ddd07d17a3236df69500',
    'gg/vape/ui/click/component/GuiComponent.java': 'fdb7b075c7a63d0a928da49d4b960aaa9cd7a40f9a78a7bca61da3417ae11144',
    'gg/vape/ui/click/component/PopupMenuButtonComponent.java': '629ff94c779e2a7cf5c0333b9f1fc64000decee595ea2dc974eb357a45397a37',
    'gg/vape/ui/click/component/SelectableTextRowComponent.java': 'e862973ef581a1ade40b19b37c825b03050ff69ce0f424ae08e527605faaf9cb',
    'gg/vape/ui/click/component/TextInputComponentBase.java': '9287c882486be519377011aeaf634798d152009f5a4ce70f2f1ace46bcd5c56d',
    'gg/vape/ui/click/component/input/BindValueRowComponent.java': '9bfa7d2cf4f3190d5e4b7003ab83963b8a271998e8187d24c770401abfe3e364',
    'gg/vape/ui/click/component/input/BindableInputComponent.java': '2c5770f933a80f9f22c83061fd8544ae3a3a3929edf1e484b945953e93a7d25b',
    'gg/vape/ui/click/component/input/BindableInputComponentCaptureToggleClickHandler.java': '5cb3d1486d48d958dd8ea4278d08b125be12a700812eb66104e8e6a78a8e4298',
    'gg/vape/ui/click/component/input/BindableInputComponentSavingBindCaptureTask.java': 'c93348e76192f41bb0809353b38567556991727f3ece3ab7be9c00961efdf49e',
    'gg/vape/ui/click/component/module/ModuleComponent.java': '01eed0131e3824e398542c40786f039bf7c19cac81dd611fab2fc6de00276bb9',
    'gg/vape/ui/click/component/value/ColorValueDropdownComponent.java': '90779af93237dfd4723d05326fac750d9696c38892d2f756a32724f4aa99dc9f',
    'gg/vape/ui/click/component/value/FloatingValueDropdownLayer.java': '677c59287ed640ccd4e65edafd2a326c60d949e863cee1a641f332ad98446037',
    'gg/vape/ui/click/component/value/ListValueComponent.java': '03190e938cc6abfaff31aacdda34e4a3a100931c0eb4ab9f94ba6ae7eac82b88',
    'gg/vape/ui/click/component/value/SearchBlockListComponent.java': 'f357f3271bef66f04dd5bb8283d130d26f099f92904515bc8a4af17a5426fab3',
    'gg/vape/ui/click/component/value/StringMapEntryComponent.java': '79b49b711b5d06c4c6fc6fc1c5734a99d5db1133ef2f4b52e9552c30549375f5',
    'gg/vape/ui/click/component/value/StringMapEntryRemoveHandler.java': 'a0d7835d9181c067f9a9e1b185e9e10a5e7ef65abda17f180a05a72694c2b045',
    'gg/vape/ui/click/component/value/StringMapValueComponent.java': 'af10e2c57445279fea246ce7ba8b990e6ec7b202e2d6feec5af36bdcc1fcccc1',
    'gg/vape/ui/click/component/value/StringValueTextInputComponent.java': '783fcb0acb3b584e79e12f9f8abdb6a361d084f7e5624625646209261ac3189d',
    'gg/vape/ui/click/component/value/ValueComponentFactory.java': '7655654f4ec3fbb834b5f8e36fd050d95273e98e8a5596331b591884b9a2254e',
    'gg/vape/ui/click/frame/FrameComponent.java': '4ca7abbdee05fa4c04174ec380fb8edca1cf935053d7bf811724fa21766f8e97',
    'gg/vape/ui/click/frame/FrameHeaderMainLayerClickHandler.java': '8edb5eb311878db1f2705118189dfb7b5ad26cb542d84cf122debe2a54fe897d',
    'gg/vape/ui/click/frame/impl/ModuleSearchFrame.java': '917564e24223a7dfe42e8de936dc4b8ea4e8dee4c4622deab7916e7944605fe0',
    'gg/vape/ui/click/frame/impl/ModuleSearchFrameHeader.java': 'a9e9f2e200971ceaea32f67f572af3759100bbbb5e7746e6301e47bd715d8d27',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableActionRow.java': '7965aedd36c516fbf50e09c7f82d1bfb304c51214b80123a32725d2ed28daf22',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableCategory.java': '0847892ac3237e4af043f19224f7a36aa1a43eefbc5328d3cffd31e4fe683b3c',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableFrameStack.java': 'c185c2c16f5b1c726f4082bb2aad37d8da0cbaf87406290d4f82a851d9cdebd8',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableLayout.java': 'ebcba85260d55bc94b4968d23f84339ae0089215defb3240b0b6bc212c5e2114',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleFrame.java': 'e52ead991f0d086992b699014d23ba021173f7a0aa3f38cc85c822f96887e08b',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleHeader.java': 'c3e778de6fc800df648651da5d6bb201a5ada67efc631d5dc218a80c86968aea',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelFrame.java': 'b6965a31884e2e395044a3cb99ff41bacd201e266c385cf70754d4b843294a41',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelHeader.java': 'cbf9ba1ce712ba2cd3fc7aac5f56ecc55347013c335e8146420ec4ecbdd0c45b',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelStyle.java': 'a7057c78082ee8cd4a0eca1aaf323b223fb0be5589c59c4f821b0f0277b2194c',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableRenderScope.java': '57b23cbd78148781ac4af9891fde02947cce1bfff74c5f1087e80ebba4f029cb',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableReturnFrame.java': '8b7038283031eeca629843740a79fac9394b26ef9c2764c55c41b1a04f5dabcf',
    'gg/vape/ui/click/frame/impl/main/ClickGuiFrameManager.java': 'bc6b768e834df00adc6fd483222f8818be58448f5f489f2d924fced58df44286',
    'gg/vape/ui/click/frame/impl/main/ClickGuiProfilesPage.java': '017e8314b34b7896c5cfe71e73f4984213ac55ae5b171e26493d959181161a1c',
    'gg/vape/ui/click/frame/impl/profile/ProfileCreateNameInputComponent.java': '0b17ea0932a2c2bd9313531b463863f610f2f2008db983fc020bf082f970b53a',
    'gg/vape/ui/click/frame/impl/profile/ProfileCreatePanelComponent.java': '7d40dff94caaac75fdd045a286c43b7c341b16099487a45337a4a4f36b5f9e57',
    'gg/vape/ui/click/frame/impl/profile/ProfileCreateSubmitNameInputComponent.java': 'df55e96e82f5dd9e9ae4c592902dcf681f415889f0a393486cd2e027b19ba1b2',
    'gg/vape/ui/click/frame/impl/profile/ProfileListEntryComponent.java': '114530d251313d80bbd0038dbacedead74e932d195b7ef758155bb59193acda2',
    'gg/vape/ui/click/frame/impl/profile/ProfileListEntryOpenButtonComponent.java': 'a5f1fd29acf7ba278aea45f354b96a0ac8f69fbd5ec72c877c07dffb0acb2b21',
    'gg/vape/ui/click/frame/impl/profile/ProfileListEntryOpenSettingsClickHandler.java': '3d0d154eda33aeabdf6df059d79a2ad275044e15cecf8d13af01ae3c2a1c5445',
    'gg/vape/ui/click/frame/impl/profile/ProfileModuleSnapshotListComponent.java': 'f6675c5cc69cfe60484f8d97ae68e81f700be3f9e3c51f4d20d804b3b28941f0',
    'gg/vape/ui/click/frame/impl/profile/ProfileModuleSnapshotRowComponent.java': '22bcffc4f3c66fb06e6254dcc7cd78adbe6cd4728c9a6beff5ed324cfc64d4fb',
    'gg/vape/ui/click/frame/impl/profile/ProfilePublishEditorPanel.java': 'ea59877c6e7e486c6d7a17df93177930ac8b004441e19bab1f1f18ed7c554b9b',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotApplyBarComponent.java': 'bcff9858533ec1b2faf81189d063e714265f3570d3d3ca9af673b79fcc435728',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotFrame.java': '816c517ad31b7e9578b5f7f0d8dfb3d9fdd90896e5af9ad66a60630d6b4b176c',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotGuiBuilder.java': '2fe5eef2281d3b0b886d42be354b368a63296486198ce049a78da6f133b7b829',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotModuleDetailsPanel.java': '9822c30c611f13e5a53a3c39808711e3b0159a79a4af5bc973f5c1a1df50116a',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotModuleListPanel.java': 'fe6151eec1d74c3d6da5fd250adb6fc965b650dde1d1d1bee7bc462d4633e966',
    'gg/vape/ui/click/frame/impl/profile/PublicProfileOwnerDetailsPanel.java': '668b61e95f9d123a5c4dabadf8fd409c53787093ec94e12059b45f049b79519f',
    'gg/vape/ui/click/frame/impl/profile/PublicProfileSnapshotPanelBase.java': '7a5856d1dc485a9d04406a65b07371b6c2c332c106485070657dd1be0d9c0b50',
    'gg/vape/ui/click/frame/impl/profile/PublicProfilesFrame.java': '73e4c7d2d2b29a6ca53084b97b6e4373bea5bdf8611e6c3d8c8f96f19e3a0d2d',
    'gg/vape/ui/click/layout/ComponentLayout.java': '279a706d64df5a0a9e94a8361fb64b14eea747816fd16d91923c07e1711cef1d',
    'gg/vape/ui/font/FontOption.java': 'd323a3306e2596a0612b38bd3db4e8716e5178603f98a2dc57f20daeb02eb941',
    'gg/vape/ui/unmap/SearchBlock.java': '93c5689ea772c35a587217e4f98fe4fb6803093bd66f2c8d4201a2a68df234f9',
    'gg/vape/unmap/Bendable.java': 'a6a539c7407fac2ca19de5d726f7049d0ea664becd8da2c2f0ae96f9c25e9de3',
    'gg/vape/unmap/ColorUtil.java': '061bfe8423bba663685689fa9b56b1f757da1cc6aefaeb237ae1f9d792cb1d7c',
    'gg/vape/unmap/ItemLimitData.java': 'b8189143683d420a0f5eb7a3aa148c3b50afcdfbf3564301b04b5b453196d450',
    'gg/vape/unmap/ModeSelection.java': 'b9f9e7688b0b22ad22d3aa6dff2e9e4be760a77cbe1385afb86e2db74e66c9c5',
    'gg/vape/utils/ThreadBoundExecutor.java': 'a2274edf80aba5c51dd8eefb61e19b6850c7aea221f0e6ea18535dfe287fac60',
    'gg/vape/utils/processors/SearchProcessor.java': '5c56e04d99638c1b792e4ae11a68d07bc06a8851b01bff87b7a08bc18f62d0ab',
    'gg/vape/utils/processors/SearchResultIndex.java': '36034fa4b16747487a6e39773bef017cb483d165ed824149c7fc511e6d1df0c2',
    'gg/vape/utils/processors/SearchScanCursor.java': '2681d1941baa725b2601cfb49296e72c4d26797fa10c428e87ae99750fa4a13b',
    'gg/vape/utils/render/RenderUtil.java': 'bc01b189ca3b5210148a225635678a4315d1fde246addae826642d7d0b003ce5',
    'gg/vape/utils/render/WorldOverlayGlState.java': '6cda41432aec210105e9046ff95a004e174936ff93b88823c7a28eac8d8995c4',
    'gg/vape/value/BindValue.java': 'f921ba964560a1e960dcc27ca99ffa7450fa61e7a5ff9f11302ac1fd552f4a20',
    'gg/vape/value/ColorValue.java': '184be31dc5789ba616a21e5f4de9c433a809e7b36ddeb8bcbf870b3414722265',
    'gg/vape/value/EntityTargetFilterValue.java': 'f322decbb1dc38bc60c9ff8a6eb89c6a5c34aa3758929a98f92eebf54990a37c',
    'gg/vape/value/LimitValue.java': '3e9b26f9b9b82173a68a9d829b6cdab11b34cd1fcdae185599fe3d246b05105d',
    'gg/vape/value/ListValue.java': 'c4fe4ba034ab55e1bd85739fe14bb62114c313cfe07351b2c2f56f28cd67061e',
    'gg/vape/value/ModeValue.java': '7affba5c300cd8516ba4a2450f40b8672970340a3592ce9d8cceb5feac6adc0b',
    'gg/vape/value/NumberValue.java': '6af63da764ed9c31c61afc615866cc94a6dd72dd5214e344d1982d970a4b5a36',
    'gg/vape/value/OptionalLimitEntry.java': '54c65aa2a07b74d4f8b62443aa2467aff4d02d1437a2c2bf2f0891ad26a78d2f',
    'gg/vape/value/OptionalLimitValue.java': '19466af5d97a1fb79d7463abab80fd61416a533c6fe8c224816724f8f99c4e33',
    'gg/vape/value/RandomClickDelayValue.java': 'f7dd45157a9ab0645b60c8ade06c0794ef86c8ecfcdacd662bae1c59dd0b70d7',
    'gg/vape/value/RandomValue.java': 'db1e26486f531500ec83b1a67f6e0c4078e90ab24731fb0f755261ec623e32f3',
    'gg/vape/value/StringMapValue.java': 'a3ca88865de78a89e71ec86415659fc3f1436413163071371162822e91525176',
    'gg/vape/value/StringValue.java': 'c3d4db1c47cdd946ec3c72989a647bd875f6ab931bc157931738730be3d601cb',
    'gg/vape/value/Value.java': 'f0769af26c0f0203c7d2dea071f19b5d4963223c65e0ee491586006eaeef5ef8',
    'gg/vape/value/ValueCondition.java': '957f1b4ddc83495b2ba0671d2ee014b625674c10608e2097f982345ff1b307df',
    'gg/vape/value/ValueSnapshot.java': '3c9214fbfdcb64a7db21c7f6c9a5d75000af4c6d97edd50ddd1342e965a58bb8',
}
JAVA_ADDITIONS = {
    'gg/vape/brand/InevitableBrand.java',
    'gg/vape/brand/InevitablePaths.java',
    'gg/vape/config/ProfileDataRetention.java',
    'gg/vape/module/combat/automace/AimSelectionRules.java',
    'gg/vape/module/combat/automace/AutoMaceAttackGate.java',
    'gg/vape/module/render/spawnerfinder/SpawnerNameMatcher.java',
    'gg/vape/module/render/storage/StorageClusterFilter.java',
    'gg/vape/rotation/RotationMath.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableActionRow.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableCategory.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableFrameStack.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableLayout.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleFrame.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleHeader.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelFrame.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelHeader.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelStyle.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableRenderScope.java',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableReturnFrame.java',
    'gg/vape/utils/processors/SearchResultIndex.java',
    'gg/vape/utils/processors/SearchScanCursor.java',
    'gg/vape/utils/render/WorldOverlayGlState.java',
}
JAVA_ADDED_CLASSES = {
    'gg/vape/brand/InevitableBrand.class',
    'gg/vape/brand/InevitablePaths.class',
    'gg/vape/config/ProfileDataRetention.class',
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
    'gg/vape/ui/click/frame/impl/inevitable/InevitableActionRow$1.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableActionRow.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableCategory.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableFrameStack.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableLayout.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleFrame.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableModuleHeader.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelFrame$Kind.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelFrame.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelHeader.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitablePanelStyle.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableRenderScope$1.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableRenderScope.class',
    'gg/vape/ui/click/frame/impl/inevitable/InevitableReturnFrame.class',
    'gg/vape/ui/click/frame/impl/profile/ProfileSnapshotGuiBuilder$1.class',
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
MODULE_EXCEPTIONS = set()

# All other native files, including the reflective-loader implementation and
# bridge, must match the baseline. Reviewed translations and loader integrity
# changes are pinned by content; binary assets are compared byte-for-byte.
APPROVED_NATIVE = {
    'native/CMakeLists.txt': 'b3e38605fc72bde08b3919c1fc200b2ee77d57c8d1fb72e1f0a49449895f5545',
    'native/brand_version.h.in': '3eebd1f90ec98b677b50128897c475faaad44b258dcc8e05f35952f76a2912c2',
    'native/client_data_path.h': '25aa95db3da49eb37eeb97ec33e26131d7e2fad7810ef8258051e1f66b2f79be',
    'native/dllmain.c': 'ab09419af2aa502121724b26705850575e2bcf44d76162e560e65050c65aecad',
    'native/inevitable.ico': '9e5f617081b410ae3d54354ad9881a71379a26fbdcaf8c7d32eec6fa82c38887',
    'native/injector.c': '5d70b219a8cca5bcc9939e6f58847228400222f8f7cdead99c943a221eb95256',
    'native/injector_icon.rc': '982fd9b2ed1a3183940ce59da18fa8c2dd3a43a10ede11820917568c5aa8b314',
    'native/loader_gui/assets/logo.png': 'e39462748839a9556e55ad6a9bd8814e5974c95bbdd1b756034287e494e2aeba',
    'native/loader_gui/controller_model.cpp': 'e7c5548173472ed7f760154886e4ebd0708c2f878ac3a78fb6054c30672037f1',
    'native/loader_gui/controller_model.h': '3beafc76870575c7776836987eca4b2aeded6769e2674aae8281ef58aee004f5',
    'native/loader_gui/controller_ui.cpp': 'e4a20083e087cf7eb7f8b77d8b0d6bfc50960ed20829f05cc3054e528d6a2637',
    'native/loader_gui/embedded_dll_materializer.cpp': 'c470d3497cd4dd38c2e5f688c2e8eebefdc7773131eee6b42f31d6487c2705c7',
    'native/loader_gui/embedded_dll_materializer.h': '61b6dbe7d36f68e7e4b454f984cd08ba3a369226ea650b21027f97e328f30d9e',
    'native/loader_gui/embedded_resource_integrity.h': 'f05b3c7e458e647768b15cade70e6ffb7e8cc03a582df6b046cd236e3ae80385',
    'native/loader_gui/icon.rc': 'f83fed0bf2fd439873b2436181060726667bba384b48d0144a7ffcccc574ccec',
    'native/loader_gui/injection_coordinator.cpp': '84730e2706766f716648072eb8e5153e9c0af7e851edfe5c877af6483f120494',
    'native/loader_gui/loader_language.cpp': '2ab4680092e008f9f547e9960a0f0c738c9dda7631f0a812b51617322c9e823a',
    'native/loader_gui/loader_language.h': '06ec241e8626d994baeba1a5b54f723baa8fc7f27bb13b46ca5c0b0e73d8b2f7',
    'native/loader_gui/loader_strings.cpp': '561990bb49071069fafdcab1b910b7f86627efb55def7f08f06403965d8383ae',
    'native/loader_gui/loader_strings.h': '252956b897d714475779f7d246b47f526e3804f7b4d4706c154918954b7d1892',
    'native/loader_gui/locked_resource_file.h': '962de2b110e7b4350fef1caff4194d78eefff4fe3243c6c8c3a362575bd30583',
    'native/loader_gui/main.cpp': 'c0e53f00ea8ceadc1672a068c833cc7253a1efb302b53225e054621b6cfa9b86',
    'native/safe_file_materialization.c': 'db608a9b7fec00024ddf7d322e8842fab059bec5a28f3b18186dd1047c8f3bc0',
    'native/safe_file_materialization.h': 'bb9a84dda8d1a25afaae878ce1b2e605e13983d3c3297ae4d9cc3f5ea18bed7b',
    'native/tests/client_data_path_test.c': '23eba784649fd618f88c949180031c9dabd12135d24f478c4fd77a1b38596e40',
    'native/tests/embedded_dll_materializer_test.cpp': '007015ad9066f5004ce351c01ba676d7e936c82f41037e5a10c933ec780c3dfa',
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
    'native/inevitable.ico',
    'native/loader_gui/embedded_dll_materializer.cpp',
    'native/loader_gui/embedded_dll_materializer.h',
    'native/loader_gui/embedded_resource_integrity.h',
    'native/loader_gui/loader_language.cpp',
    'native/loader_gui/loader_language.h',
    'native/loader_gui/locked_resource_file.h',
    'native/safe_file_materialization.c',
    'native/safe_file_materialization.h',
    'native/tests/client_data_path_test.c',
    'native/tests/embedded_dll_materializer_test.cpp',
    'native/tests/embedded_resource_integrity_test.cpp',
    'native/tests/loader_language_test.cpp',
    'native/tests/locked_resource_file_test.cpp',
    'native/tests/materializer_fixture.rc',
    'native/tests/safe_file_materialization_test.cpp',
}
APPROVED_RESOURCES = {
    'src/main/resources/META-INF/inevitable-version.properties': '0b18bfe6233ecc75ee56c427950754509cb0827c2b810b893b08c75c5dd26eac',
    'src/main/resources/resources/chinese.properties.txt': '6944cf232204cbcf0be9c437082b9f54d0b1cfd264b3f7c9b9df3c980f58ade4',
    'src/main/resources/resources/english.properties.txt': '6cc2d3e49e241a0c3370d7d945a9a7f17f01a53a5b8668259e7150895f767ea3',
    'src/main/resources/resources/french.properties.txt': 'cc0fbdb77513a4e4323d8be002840dbbfb6fce4772592c7f337ea19174c20a71',
    'src/main/resources/resources/textures/v4.png': '5e612cd45fd657b4ebfadd752ea9219b4e9cd32456cff3707c06878a7846c4f9',
    'src/main/resources/resources/textures/vapelogo.png': '6121c4c831eb27fe572b8791f9192de6e54e7075f17bd9a59eff85ee6badbde8',
}
NATIVE_DELETIONS = {
    'native/vape-v4.21.ico': 'cd857a1b00744675357b7fc4d889e7a0845920d2fae0111ece9b475e193df3a2',
}
RESOURCE_ADDITIONS = {
    'src/main/resources/META-INF/inevitable-version.properties',
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
    'resources/textures/v4.png': '5e612cd45fd657b4ebfadd752ea9219b4e9cd32456cff3707c06878a7846c4f9',
    'resources/textures/vapelogo.png': '6121c4c831eb27fe572b8791f9192de6e54e7075f17bd9a59eff85ee6badbde8',
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
        "InevitableClient build: module parity audit against the recovered 4.21.58 baseline",
        "",
        "Baseline source commit: " + BASE_COMMIT,
        "Original release EXE SHA-256 verified: " + ORIGINAL_SHA256,
        "InevitableClient EXE SHA-256: " + sha256(current_exe),
        "InevitableClient DLL SHA-256: " + sha256(embedded_dll),
        "InevitableClient source-build Java payload SHA-256: " + sha256(current_jar),
        "InevitableClient embedded Java payload SHA-256: " + sha256(embedded_jar),
        f"Java baseline sources: {source_count}; module sources: {module_source_count}; reviewed changes/additions: {changed_sources}.",
        f"Module sources: {len(MODULE_EXCEPTIONS)} reviewed localization edits and {len(ENHANCED_MODULE_SOURCES)} reviewed behavioral changes, plus pinned additions; all others match baseline.",
        "Reviewed source changes are pinned by complete source hashes.",
        "Behavioral improvements intentionally differ from the baseline; they require runtime testing.",
        f"Native files: {native_count} baseline files; {native_changes} reviewed localization/integrity/build/test changes or additions.",
        f"Resource files: {resource_count} baseline files; {resource_changes} reviewed translation, artwork or version changes.",
        f"Same-toolchain builds: {stats['matching_module_classes']}/{stats['module_classes']} module class files are byte-identical.",
        "Differing baseline module classes (reviewed localization and behavioral changes):",
        *("  " + name for name in stats["module_changes"]),
        f"Owned class inventory: {stats['own_classes']} baseline files plus {len(JAVA_ADDED_CLASSES)} reviewed additions; no unreviewed class differences.",
        f"Non-language product resources ({stats['resources']} files) match except for pinned artwork changes.",
        f"Embedded payload has the same {embedded_resource_count} non-class resources as the InevitableClient source build.",
        "Native loading changes are pinned above; the EXE and optional DLL embed the expected JAR. This audit does not establish safety.",
    ]
    if stats["release_layout"] is None:
        official_matches = stats["module_classes"] - len(stats["release_differences"])
        lines += [
            f"Original release vs same-source baseline: {official_matches}/{stats['module_classes']} module class files byte-identical.",
            f"Original release vs InevitableClient build: {stats['release_english_matches']}/{stats['module_classes']} module class files byte-identical.",
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
        emit_notice("Original release comparison", f"Original EXE hash verified; {official_matches}/{stats['module_classes']} module class files match the baseline build, and {stats['release_english_matches']}/{stats['module_classes']} match the InevitableClient build byte-for-byte.")
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
