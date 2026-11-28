#!/usr/bin/env python3
"""Test real value and profile-snapshot serialization with Gson and headless services.

This does not exercise the Minecraft runtime, complete profile manager or GUI.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "org/jetbrains/annotations/Nullable.java": "package org.jetbrains.annotations; import java.lang.annotation.*; @Target({ElementType.TYPE_USE,ElementType.METHOD,ElementType.PARAMETER,ElementType.FIELD}) public @interface Nullable {}",
    "gg/vape/Vape.java": """package gg.vape; import gg.vape.value.Value; public class Vape {
        public static final Vape INSTANCE=new Vape(); public int saves; public final Registry values=new Registry(); public final ModManager mods=new ModManager(); public final ProfilesManager profiles=new ProfilesManager(); public void saveAndStop(){saves++;} public static void logThrowable(Throwable t){}
        public Registry getSettingsManager(){return new Registry();} public Registry getValueManager(){return values;}
        public Friends getFriendManager(){return new Friends();} public Enemies getEnemyManager(){return new Enemies();}
        public ItemHelper getItemHelper(){return new ItemHelper();} public ModManager getModManager(){return mods;} public ProfilesManager getProfilesManager(){return profiles;}
        public gg.vape.config.ClientSettings getClientSettings(){return new gg.vape.config.ClientSettings();}
        public static class Registry {public int registrations; public void registerValue(Value<?,?> value){registrations++;}}
        public static class ModManager {public final java.util.List<gg.vape.module.Mod> top=new java.util.ArrayList<>(); public java.util.List<gg.vape.module.Mod> getTopLevelModules(){return top;}
            public gg.vape.module.utility.InventoryManager getMod(Class<?> type){return new gg.vape.module.utility.InventoryManager();}}
        public static class ProfilesManager {public gg.vape.config.Profile active; public int reloads; public boolean reloadLocked; public final java.util.List<gg.vape.config.Profile> all=new java.util.ArrayList<>();
            public java.util.List<gg.vape.config.Profile> getProfiles(){return all;} public gg.vape.config.Profile getProfileByOnlineId(java.util.UUID id){return null;} public gg.vape.config.Profile getActiveProfile(){return active;} public gg.vape.config.Profile getActiveProfileOrNull(){return active;}
            public boolean captureIfActive(gg.vape.config.Profile p){if(active!=p || p==null)return false;p.captureCurrentState();return true;}
            public void reloadActiveProfile(gg.vape.config.Profile p){reloadLocked=Thread.holdsLock(p);reloads++;}}
        public static class Friends {public boolean isFriend(Object entity){return false;}}
        public static class Enemies {public boolean isEnemy(String name){return false;}}
        public static class ItemHelper {public boolean matchesItem(String name,gg.vape.wrapper.impl.ItemStack stack){return false;}}
    }""",
    "gg/vape/unmap/PropertyContainer.java": "package gg.vape.unmap; public class PropertyContainer {}",
    "gg/vape/config/PublicProfileSettings.java": "package gg.vape.config; public class PublicProfileSettings {}",
    "gg/vape/config/Profile.java": """package gg.vape.config; import java.util.*; import com.google.gson.*; import gg.vape.module.Mod; public class Profile {
        private JsonObject data=new JsonObject(), published; public boolean dirty; public Profile(){} public Profile(String n,String v){} public JsonObject getData(){return data;} public void updateData(JsonObject d){data=d;}
        public Set<Mod> getEnabledModules(){return Collections.emptySet();} public synchronized void captureCurrentState(){} public void setDirty(boolean d){dirty=d;} public String getClientVersion(){return \"4.21\";}
        public Profile loadJson(JsonObject o){return this;} public JsonObject toJson(boolean b){return data;} public void setPublishedData(JsonObject d){published=d;}
        public JsonObject copyPublishedData(){return published;} public void setDraft(boolean b){} public JsonObject getPublishedData(){return published;} public void setName(String n){} public ProfileSnapshot createSnapshot(boolean b){return null;}}
    """,
    "gg/vape/config/PublicProfile.java": """package gg.vape.config; import java.util.*; public class PublicProfile {public String getName(){return \"public\";}
        public Map<String,Object> getData(){return null;} public ShareInfo getShareInfo(){return new ShareInfo();} public static class ShareInfo {public UUID getDerivedFrom(){return null;}}}
    """,
    "gg/vape/api/ApiHttpClient.java": "package gg.vape.api; public class ApiHttpClient {public static final com.google.gson.Gson GSON=new com.google.gson.Gson();}",
    "gg/vape/config/ProfileModuleSnapshotOrderComparator.java": "package gg.vape.config; import java.util.Comparator; public class ProfileModuleSnapshotOrderComparator implements Comparator<ProfileModuleSnapshot> {public int compare(ProfileModuleSnapshot a,ProfileModuleSnapshot b){return 0;}}",
    "gg/vape/utils/NameComparator.java": "package gg.vape.utils; import java.util.Comparator; import gg.vape.unmap.INamed; public class NameComparator implements Comparator<INamed> {public int compare(INamed a,INamed b){return a.getName().compareToIgnoreCase(b.getName());}}",
    "gg/vape/module/render/hud/HudModule.java": "package gg.vape.module.render.hud; import gg.vape.module.Mod; public class HudModule extends Mod {public HudModule(String name){super(name);}}",
    "gg/vape/module/Category.java": "package gg.vape.module; public class Category {public static final Category NONE=new Category(), COMBAT=new Category();}",
    "gg/vape/module/Mod.java": """package gg.vape.module; import java.util.*; import gg.vape.input.BindSet; import gg.vape.value.*;
        public class Mod { public final List<Value<?,?>> values=new ArrayList<>(); private final String name; private final BindSet bind=new BindSet(); public int defaultKeybind; public boolean enabled; public Category category=Category.COMBAT;
        public Mod(){this(\"stub\");} public Mod(String name){this.name=name;} public String getName(){return name;}
        public List<Value<?,?>> getAllValues(){return values;} public void addValue(Value<?,?>... v){Collections.addAll(values,v);}
        public Category getCategory(){return category;} public boolean isDefaultVisible(){return true;} public boolean isEnabled(){return enabled;} public BindSet getBind(){return bind;} public int getDefaultKeybind(){return defaultKeybind;}
        public void p(SubModuleValue a,SubModuleValue b){} }""",
    "gg/vape/module/SubModule.java": "package gg.vape.module; public class SubModule extends Mod { public Object getParent(){return null;} }",
    "gg/vape/module/utility/inventory/HotbarSlotRule.java": """package gg.vape.module.utility.inventory; import com.google.gson.*; import gg.vape.wrapper.impl.ItemStack; public class HotbarSlotRule {
        private int id; public HotbarSlotRule(int id){this.id=id;} public int getItemId(){return id;} public ItemStack createItemStack(){return null;}
        public void loadJson(JsonObject o){id=o.get(\"item-id\").getAsInt();} public JsonObject toJson(){JsonObject o=new JsonObject();o.addProperty(\"item-id\",id);return o;}}
    """,
    "gg/vape/module/utility/inventory/HotbarSlotRuleAddButton.java": """package gg.vape.module.utility.inventory; import gg.vape.ui.click.component.*; public class HotbarSlotRuleAddButton extends GuiComponent {
        private GuiClickListener listener; public void addClickListener(GuiClickListener l){listener=l;} public void click(){listener.onPrimaryClick();}}
    """,
    "gg/vape/module/utility/inventory/HotbarSlotRuleItemPickerFrame.java": """package gg.vape.module.utility.inventory; import gg.vape.ui.click.frame.FrameStackManager;
        public class HotbarSlotRuleItemPickerFrame {public void setGroupComponent(HotbarSlotRuleGroupComponent group){} public void setParentStackManager(FrameStackManager stack){}
        public void t(boolean a,boolean b){} public Object getItemListFrame(){return new Object();}}
    """,
    "gg/vape/module/utility/InventoryManager.java": "package gg.vape.module.utility; import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue; public class InventoryManager {public InventoryCleanerProfileValue getProfileValue(){return new InventoryCleanerProfileValue(null,\"inventory\");}}",
    "gg/vape/module/utility/inventory/cleaner/SlotInventoryFilterRule.java": """package gg.vape.module.utility.inventory.cleaner; import com.google.gson.*; public class SlotInventoryFilterRule {private int slot;
        public SlotInventoryFilterRule(int slot){this.slot=slot;} public SlotInventoryFilterRule(JsonObject o){slot=o.get(\"slot\").getAsInt();}
        public int getSlot(){return slot;} public JsonObject toJson(boolean embed){JsonObject o=new JsonObject();o.addProperty(\"slot\",slot);return o;}}
    """,
    "gg/vape/module/utility/inventory/cleaner/ItemInventoryFilterRule.java": """package gg.vape.module.utility.inventory.cleaner; import com.google.gson.*; import gg.vape.wrapper.impl.ItemStack; public class ItemInventoryFilterRule {
        public ItemInventoryFilterRule(JsonObject o){} public JsonObject toJson(boolean embed){return new JsonObject();} public boolean matches(ItemStack stack){return true;}
        public Selection getItemSelection(){return new Selection();} public static class Selection {public boolean matches(ItemStack stack){return true;}}}
    """,
    "gg/vape/ui/click/GuiMouseEvent.java": "package gg.vape.ui.click; public class GuiMouseEvent {}",
    "gg/vape/ui/click/component/GuiClickListener.java": "package gg.vape.ui.click.component; public interface GuiClickListener {void onPrimaryClick(); default void onSecondaryClick(){}}",
    "gg/vape/ui/click/frame/FrameComponent.java": "package gg.vape.ui.click.frame; public class FrameComponent {public void l$src$V$1mibm4x(){}}",
    "gg/vape/ui/click/frame/FrameStackManager.java": "package gg.vape.ui.click.frame; public class FrameStackManager {public void q(Object f){} public void R(Object a,Object b){}}",
    "gg/vape/ui/click/frame/impl/main/ClickGuiFrameManager.java": "package gg.vape.ui.click.frame.impl.main; import gg.vape.ui.click.frame.FrameStackManager; public class ClickGuiFrameManager extends FrameStackManager {public void setSidecarFrame(Object f){}}",
    "gg/vape/ui/click/component/GuiComponent.java": """package gg.vape.ui.click.component; import java.util.*; import java.awt.Color; import gg.vape.ui.click.GuiMouseEvent; import gg.vape.ui.click.frame.FrameComponent;
        public class GuiComponent {public static final Theme J=new Theme(); public static class Theme {public Color K=Color.WHITE,m=Color.BLACK,r=Color.GRAY,Z=Color.WHITE;}
        private final List<GuiComponent> children=new ArrayList<>(); private boolean visible=true; private gg.vape.value.Value<?,?> bound;
        public List<GuiComponent> f(){return children;} public void addChildren(GuiComponent... c){Collections.addAll(children,c);} public void h(GuiComponent c,Object... options){children.add(c);}
        public void setVisible(boolean b){visible=b;} public boolean V$src$Z$1xhop3l(){return visible && (bound==null || !bound.isHidden() && bound.areConditionsMet());}
        public void bindValue(gg.vape.value.Value<?,?> v){bound=v;v.setBoundComponent(this);} public void setUseExplicitWidth(boolean b){} public void setExplicitWidth(double d){} public void setHorizontalInset(double d){}
        public FrameComponent getParentFrameComponent(){return null;} public void onDisable(){} public gg.vape.ui.font.SmoothFontRenderer getFontRenderer(double scale){return new gg.vape.ui.font.SmoothFontRenderer();} public Color getDisabledOverlayColor(){return Color.BLACK;} public double x(){return 0;} public double C(){return 0;} public double A(){return 110;} public double L(){return 10;} public double n(){return 0;}
        public double G$src$D$1b2f02a(){return 0;} public void K(double x){} public void S(double y){} public void Y(double h){} public void u(){} public void I(){} public void H(){} public void F(){} public void g(GuiMouseEvent e){}}
    """,
    "gg/vape/ui/click/component/FlowLayoutComponent.java": """package gg.vape.ui.click.component; public class FlowLayoutComponent extends GuiComponent {public FlowLayoutComponent(double width){} public Layout l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij(){return new Layout();} public static class Layout {public void Q(boolean b){}}} """,
    "gg/vape/ui/click/component/SimpleTextLabelComponent.java": """package gg.vape.ui.click.component; public class SimpleTextLabelComponent extends GuiComponent {public SimpleTextLabelComponent(String text,double scale){}} """,
    "gg/vape/ui/font/SmoothFontRenderer.java": """package gg.vape.ui.font; import java.awt.Color; public class SmoothFontRenderer {public double d(String s){return 8;} public void d(String s,double x,double y,Color c){} public double N(String s){return s.length();}} """,
    "gg/vape/ui/click/component/value/ColorPaletteSliderComponent.java": """package gg.vape.ui.click.component.value; import java.awt.Color; import gg.vape.ui.click.component.GuiComponent; import gg.vape.value.ColorValue; public class ColorPaletteSliderComponent extends GuiComponent {
        private final ColorValue value; private final Color[] colors; private int selected,pending; public ColorPaletteSliderComponent(ColorValue v,Color[] c){value=v;colors=c;}
        public ColorValue getColorValue(){return value;} public void setDisabledOverlayColor(Color c){} public void setSeparatedSegments(boolean b){} public void setToolTips(Object t){}
        public void selectPaletteIndex(int i){pending=i;value.setColor(colors[i]);} public void synchronizeSelectionFromValue(){selected=pending;} public int getSelectedIndex(){return selected;}} """,
    "gg/vape/ui/click/component/value/ColorValueDropdownHueSliderComponent.java": """package gg.vape.ui.click.component.value; import java.awt.Color; import gg.vape.value.ColorValue; public class ColorValueDropdownHueSliderComponent extends ColorPaletteSliderComponent {
        public ColorValueDropdownHueSliderComponent(ColorValueDropdownComponent d,String label,ColorValue v,Color[] c){super(v,c);}} """,
    "gg/vape/ui/click/component/input/BindableInputComponent.java": """package gg.vape.ui.click.component.input; import gg.vape.ui.click.component.GuiComponent; public class BindableInputComponent {public static void cancelCapturesInTree(GuiComponent root){}} """,
    "gg/vape/ui/click/component/gui/InteractiveComponent.java": """package gg.vape.ui.click.component.gui; import gg.vape.ui.click.component.*; public class InteractiveComponent extends GuiComponent {private GuiClickListener listener; public void setClickListener(GuiClickListener l){listener=l;} public void dispatchPrimaryClick(){if(listener!=null)listener.onPrimaryClick();}} """,
    "gg/vape/ui/click/component/value/BooleanToggleComponent.java": """package gg.vape.ui.click.component.value; import gg.vape.ui.click.component.GuiComponent; import gg.vape.value.BooleanValue; public class BooleanToggleComponent extends GuiComponent {private CompactList list; public BooleanToggleComponent(BooleanValue v){bindValue(v);if(v.getTerminalDependentValue()!=null)list=new CompactList();} public CompactList getCompactListComponent(){return list;} public static class CompactList {public void setUseAnchoredPopup(boolean b){}}} """,
    "gg/vape/ui/click/frame/impl/profile/ProfileSnapshotValueResetButtonComponent.java": """package gg.vape.ui.click.frame.impl.profile; import java.awt.Color; import gg.vape.value.ValueSnapshot; import gg.vape.ui.click.component.GuiComponent; import gg.vape.ui.click.component.gui.InteractiveComponent; public class ProfileSnapshotValueResetButtonComponent extends InteractiveComponent {public ProfileSnapshotValueResetButtonComponent(String s,Color c,double a,double b,double d,GuiComponent v,ValueSnapshot<?,?> snapshot){}} """,
    "gg/vape/ui/click/component/SquareIconButtonComponent.java": """package gg.vape.ui.click.component; public class SquareIconButtonComponent extends GuiComponent {private GuiClickListener listener;
        public SquareIconButtonComponent(String icon){} public void setClickListener(GuiClickListener l){listener=l;} public void dispatchPrimaryClick(){listener.onPrimaryClick();}}
    """,
    "func/skidline/RectData.java": "package func.skidline; public class RectData {public RectData(double x,double y,double w,double h){}}",
    "gg/vape/utils/render/GuiRenderPrimitives.java": "package gg.vape.utils.render; import java.awt.Color; public class GuiRenderPrimitives {public static void d(double x,double y,double w,double h,Color c){} public static void C(double x,double y,double w,double h,Color c){}}",
    "gg/vape/utils/render/ItemIconRenderer.java": "package gg.vape.utils.render; import gg.vape.wrapper.impl.ItemStack; public class ItemIconRenderer {public static void renderItemStack(ItemStack s,float x,float y,int w,int h){}}",
    "gg/vape/ui/click/component/value/ValueComponentFactory.java": """package gg.vape.ui.click.component.value; import gg.vape.ui.click.component.GuiComponent; import gg.vape.value.*; public class ValueComponentFactory {
        public static Object getComparableListEntryText(Object o){return o;} public static Value<?,?> createSnapshotProxyValue(ValueSnapshot<?,?> v){return v.createProxyValue();}
        public static GuiComponent createMainValueComponent(Value<?,?> v,boolean snapshot){if(v instanceof ListValue && v.getParent() instanceof BooleanValue && ((BooleanValue)v.getParent()).getTerminalDependentValue()==v)return null;
        if(v instanceof BooleanValue)return new BooleanToggleComponent((BooleanValue)v); if(v instanceof gg.vape.module.utility.inventory.HotbarSlotRuleValue)return new gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent((gg.vape.module.utility.inventory.HotbarSlotRuleValue)v);
        GuiComponent c=new GuiComponent();c.bindValue(v);return c;}}
    """,
    "gg/vape/input/KeyboardCodeUtil.java": "package gg.vape.input; public class KeyboardCodeUtil {public static int convertLegacyKeyCode(int k){return k+1000;}}",
    "gg/vape/input/BindActivationMode.java": "package gg.vape.input; public enum BindActivationMode {TOGGLE,HELD; public BindActivationMode toggle(){return this==TOGGLE?HELD:TOGGLE;}}",
    "gg/vape/input/KeyboardInput.java": "package gg.vape.input; public class KeyboardInput {public static boolean isKeyDown(int k){return false;}}",
    "gg/vape/input/MouseInput.java": "package gg.vape.input; public class MouseInput {public static boolean isButtonDown(int k){return false;}}",
    "gg/vape/unmap/BendableInputDispatcher.java": "package gg.vape.unmap; public class BendableInputDispatcher {public static void register(Bendable bind){}}",
    "com/google/common/collect/ImmutableList.java": "package com.google.common.collect; import java.util.*; public class ImmutableList {public static <T> List<T> copyOf(Collection<? extends T> c){return Collections.unmodifiableList(new ArrayList<T>(c));}}",
    "org/jetbrains/annotations/UnmodifiableView.java": "package org.jetbrains.annotations; import java.lang.annotation.*; @Target({ElementType.TYPE_USE,ElementType.METHOD}) public @interface UnmodifiableView {}",
    "gg/vape/module/none/ClientSettings.java": """package gg.vape.module.none; import gg.vape.value.NumberValue; import gg.vape.ui.click.frame.FrameStackManager;
        public class ClientSettings {public static ClientSettings INSTANCE=new ClientSettings(); public static FrameStackManager hotbarRuleEditorStack=new FrameStackManager(); public NumberValue rainbowSpeed;
        public static <T> T getFrame(Class<T> type){try{return type.newInstance();}catch(Exception e){throw new RuntimeException(e);}}
        public FrameStackManager getActiveStack(){return new FrameStackManager();} public void switchFrameStack(FrameStackManager s){} public void refreshFrameLayouts(){}}
    """,
    "gg/vape/config/ClientSettings.java": """package gg.vape.config; import gg.vape.wrapper.impl.*; public class ClientSettings {
        public static final String FORMAT_CODE="\\u00a7"; public static boolean isReservedEntity(Entity e){return false;} public static boolean isInputDown(int code){return false;} public boolean isTeammate(EntityPlayerSP p,EntityLivingBase e){return false;} public boolean isBot(EntityLivingBase e){return false;}
    }""",
    "gg/vape/module/macro/MacroAction.java": "package gg.vape.module.macro; public class MacroAction {}",
    "gg/vape/module/macro/CommandMacro.java": "package gg.vape.module.macro; public class CommandMacro extends gg.vape.module.Macro {public CommandMacro(String n){super(n);} public MacroAction createAction(){return null;}}",
    "gg/vape/module/macro/ItemMacro.java": "package gg.vape.module.macro; public class ItemMacro extends gg.vape.module.Macro {public ItemMacro(String n){super(n);} public MacroAction createAction(){return null;}}",
    "gg/vape/module/macro/FishingRodMacro.java": "package gg.vape.module.macro; public class FishingRodMacro extends gg.vape.module.Macro {public FishingRodMacro(){super(\"fishing rod\");} public MacroAction createAction(){return null;}}",
    "gg/vape/unmap/ColorUtil.java": "package gg.vape.unmap; import java.awt.Color; public class ColorUtil {public static Color createReadableHsbColor(float h,float s,float b){return Color.getHSBColor(h,s,b);}}",
    "gg/vape/utils/MathUtil.java": "package gg.vape.utils; public class MathUtil {public static double roundToIncrement(double n,double increment){return Math.round(n/increment)*increment;}}",
    "gg/vape/utils/TimerUtil.java": "package gg.vape.utils; public class TimerUtil {public boolean hasTimeElapsed(long millis){return false;} public void reset(){}}",
    "gg/vape/utils/StringUtils.java": "package gg.vape.utils; public class StringUtils {public static String p(String value){return value;} public static String q(java.util.List<Integer> keys){return keys.toString();}}",
    "gg/vape/utils/MutableColor.java": "package gg.vape.utils; import java.awt.Color; public class MutableColor extends Color {public MutableColor(int rgb){super(rgb);} public MutableColor withAlpha(int alpha){return this;}}",
    "gg/vape/utils/RotationUtil.java": "package gg.vape.utils; import gg.vape.wrapper.impl.EntityLivingBase; public class RotationUtil {public static boolean k(EntityLivingBase e){return false;} public static boolean b$src$Z$reqs95(EntityLivingBase e){return false;}}",
    "gg/vape/mapping/MappedClasses.java": "package gg.vape.mapping; public class MappedClasses {public static Class zS,zm,FT,lG,Yw,Fr,Zo,ur,Z8,ZP,Fb,qf;}",
    "gg/vape/wrapper/impl/Entity.java": "package gg.vape.wrapper.impl; public class Entity {public boolean isNull(){return false;} public boolean isInstance(Class c){return false;} public Object getObject(){return null;} public String getName(){return \"\";}}",
    "gg/vape/wrapper/impl/EntityLivingBase.java": "package gg.vape.wrapper.impl; public class EntityLivingBase extends Entity {public EntityLivingBase(Object o){} public float w$src$F$15l9epb(){return 1;}}",
    "gg/vape/wrapper/impl/EntityPlayerSP.java": "package gg.vape.wrapper.impl; public class EntityPlayerSP extends EntityLivingBase {public EntityPlayerSP(){super(null);} public boolean canEntityBeSeen(Entity e){return true;} public ItemStack getHeldItemHand(){return new ItemStack();} public ItemStack i(EnumHand hand){return new ItemStack();} public InventoryPlayer V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6(){return new InventoryPlayer();}}",
    "gg/vape/wrapper/impl/Minecraft.java": "package gg.vape.wrapper.impl; public class Minecraft {public static EntityPlayerSP thePlayer(){return new EntityPlayerSP();} public static int J(){return 1920;} public static int h(){return 1080;}}",
    "gg/vape/wrapper/impl/ForgeVersion.java": "package gg.vape.wrapper.impl; public class ForgeVersion {public static final ForgeVersion MC_1_7_10=new ForgeVersion(),MC_1_12_2=new ForgeVersion(),MC_1_21_4=new ForgeVersion(); public boolean Y(){return false;} public boolean d(){return false;}}",
    "gg/vape/wrapper/impl/Item.java": "package gg.vape.wrapper.impl; public class Item {public static Item L(String n){return new Item();} public int P(){return 0;} public boolean isNull(){return false;} public String getItemStackDisplayName(ItemStack stack){return \"\";}}",
    "gg/vape/wrapper/impl/ItemStack.java": "package gg.vape.wrapper.impl; public class ItemStack {public boolean isNull(){return false;} public boolean isNotNull(){return true;} public Item getItem(){return new Item();} public int L(){return 0;}}",
    "gg/vape/wrapper/impl/InventoryPlayer.java": "package gg.vape.wrapper.impl; public class InventoryPlayer {public int v(){return 0;}}",
    "gg/vape/wrapper/impl/EnumHand.java": "package gg.vape.wrapper.impl; public class EnumHand {public static EnumHand offHand(){return new EnumHand();}}",
}
REAL = [
    "src/main/java/gg/vape/unmap/INamed.java",
    "src/main/java/gg/vape/module/Macro.java",
    "src/main/java/gg/vape/unmap/Bendable.java",
    "src/main/java/gg/vape/unmap/BendableBindList.java",
    "src/main/java/gg/vape/unmap/BindChangeListener.java",
    "src/main/java/gg/vape/unmap/ItemLimitData.java",
    "src/main/java/gg/vape/input/BindSet.java",
    "src/main/java/gg/vape/unmap/ModeSelection.java",
    "src/main/java/gg/vape/unmap/ModeOption.java",
    "src/main/java/gg/vape/utils/Base64Util.java",
    "src/main/java/gg/vape/config/ConfigJsonUtils.java",
    "src/main/java/gg/vape/config/ProfileDataRetention.java",
    "src/main/java/gg/vape/config/ProfileModuleSnapshot.java",
    "src/main/java/gg/vape/config/ProfileSnapshot.java",
    "src/main/java/gg/vape/ui/click/frame/impl/profile/ProfileSnapshotGuiBuilder.java",
    "src/main/java/gg/vape/ui/click/component/value/ColorValueDropdownComponent.java",
    "src/main/java/gg/vape/module/utility/inventory/HotbarSlotRuleValue.java",
    "src/main/java/gg/vape/module/utility/inventory/HotbarSlotRuleEditorComponent.java",
    "src/main/java/gg/vape/module/utility/inventory/HotbarSlotRuleGroupComponent.java",
    "src/main/java/gg/vape/module/utility/inventory/HotbarSlotRuleGroupSelectClickHandler.java",
    "src/main/java/gg/vape/module/utility/inventory/cleaner/InventoryCleanerProfile.java",
    "src/main/java/gg/vape/module/utility/inventory/cleaner/InventoryCleanerProfileValue.java",
    "src/main/java/gg/vape/value/Value.java",
    "src/main/java/gg/vape/value/ValueAccessor.java",
    "src/main/java/gg/vape/value/DirectValueAccessor.java",
    "src/main/java/gg/vape/value/SnapshotValueAccessor.java",
    "src/main/java/gg/vape/value/ValueChangeListener.java",
    "src/main/java/gg/vape/value/ValueChangeValidator.java",
    "src/main/java/gg/vape/value/ValueCondition.java",
    "src/main/java/gg/vape/value/ConditionalValue.java",
    "src/main/java/gg/vape/value/BooleanValue.java",
    "src/main/java/gg/vape/value/NumberValue.java",
    "src/main/java/gg/vape/value/RandomValue.java",
    "src/main/java/gg/vape/value/RandomClickDelayValue.java",
    "src/main/java/gg/vape/module/none/ClientSettingsBooleanValue.java",
    "src/main/java/gg/vape/module/blatant/antibot/AntiBotBooleanValue.java",
    "src/main/java/gg/vape/module/blatant/antibot/AntiBotModeValue.java",
    "src/main/java/gg/vape/value/ColorValue.java",
    "src/main/java/gg/vape/value/EntityTargetFilterValue.java",
    "src/main/java/gg/vape/value/BindValue.java",
    "src/main/java/gg/vape/value/ToggleableListEntry.java",
    "src/main/java/gg/vape/value/OptionalLimitEntry.java",
    "src/main/java/gg/vape/value/ListValueSuggestionProvider.java",
    "src/main/java/gg/vape/value/ListValueMutableBackingList.java",
    "src/main/java/gg/vape/value/ListValue.java",
    "src/main/java/gg/vape/value/OptionalLimitValue.java",
    "src/main/java/gg/vape/value/LimitValue.java",
    "src/main/java/gg/vape/value/ModeValue.java",
    "src/main/java/gg/vape/value/SubModuleValue.java",
    "src/main/java/gg/vape/value/StringValue.java",
    "src/main/java/gg/vape/value/StringMapValue.java",
    "src/main/java/gg/vape/value/ValueSnapshot.java",
    "tools/tests/ValueProfileSerializationTest.java",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gson", required=True, help="Gson JAR (2.2.4 or 2.10.1)")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    gson = Path(args.gson).resolve()
    if not gson.is_file():
        parser.error("Gson JAR does not exist: " + str(gson))
    with tempfile.TemporaryDirectory(prefix="value-profile-test-") as directory:
        tmp = Path(directory)
        sources = []
        for name, content in STUBS.items():
            path = tmp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(str(path))
        sources += [str(ROOT / name) for name in REAL]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(gson),
                        "-d", str(classes)] + sources, cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", os.pathsep.join([str(classes), str(gson)]),
                        "ValueProfileSerializationTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
