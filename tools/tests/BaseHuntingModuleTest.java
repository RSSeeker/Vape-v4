import gg.vape.event.impl.EventRender3D;
import gg.vape.module.render.SpawnerFinder;
import gg.vape.module.render.StorageESP;
import gg.vape.module.render.spawnerfinder.SpawnerNameMatcher;
import gg.vape.module.render.storage.StorageClusterFilter;
import gg.vape.utils.datas.HSBAData;
import gg.vape.utils.datas.HSBData;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.OpenGlBackendHolder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import gg.vape.utils.render.RenderUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.value.OptionalLimitValue;
import gg.vape.wrapper.impl.*;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;

/** Real event handlers with deterministic stub mappings, world and GL backend. */
public final class BaseHuntingModuleTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void flag(Object object, String name, boolean value) throws Exception {
        ((BooleanValue)field(object, name)).setValue(value);
    }
    private static void number(Object object, String name, double value) throws Exception {
        ((NumberValue)field(object, name)).setValue(value);
    }
    private static HSBData boxAt(int x) {
        for (HSBData box : RenderUtil.boxes) if (box.F == x) return box;
        return null;
    }
    private static void resetState() {
        GuiRenderPrimitives.buffered = true;
        OpenGlBackendHolder.backend.states.clear();
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(3042);
        OpenGlBackendHolder.backend.disableCapability(2929);
        OpenGlBackendHolder.backend.disableCapability(2884);
        OpenGlBackendHolder.backend.disableCapability(3553);
        OpenGlBackendHolder.backend.line = 2.25f;
        BufferedGuiRenderPrimitives.capabilityState.depthWriteEnabled = false;
        GL11.glDepthMask(true);
        GL14.glBlendFuncSeparate(1, 0, 1, 0);
    }
    private static void setDivergentBufferedState() {
        resetState();
        OpenGlBackendHolder.backend.enableCapability(3042);
        OpenGlBackendHolder.backend.enableCapability(2884);
        GL11.glDisable(3042); // actual GL and the next buffered batch can differ
        GL11.glEnable(2929);
        GL11.glDisable(2884);
        GL11.glDepthMask(true);
        GL14.glBlendFuncSeparate(1, 0, 770, 771);
    }
    private static void checkDivergentBufferedState(String name) {
        check(BufferedGuiRenderPrimitives.capabilityState.blendEnabled
                        && !BufferedGuiRenderPrimitives.capabilityState.depthTestEnabled
                        && BufferedGuiRenderPrimitives.capabilityState.cullFaceEnabled
                        && !BufferedGuiRenderPrimitives.capabilityState.depthWriteEnabled,
                name + " restores buffered logical state");
        check(!GL11.glIsEnabled(3042) && GL11.glIsEnabled(2929) && !GL11.glIsEnabled(2884)
                        && GL11.glGetBoolean(2930), name + " restores distinct raw GL state");
        check(GL11.srcRgb==1 && GL11.dstRgb==0 && GL11.srcAlpha==770 && GL11.dstAlpha==771,
                name + " restores separate RGB and alpha blend factors");
        check(OpenGlBackendHolder.backend.line==2.25f && RenderUtil.matrices==0
                        && Minecraft.renderer.starts==Minecraft.renderer.ends,
                name + " balances matrix, lightmap and line width");
    }
    private static void checkState(String name) {
        check(!OpenGlBackendHolder.backend.isCapabilityEnabled(3042)
                && !OpenGlBackendHolder.backend.isCapabilityEnabled(2929)
                && !OpenGlBackendHolder.backend.isCapabilityEnabled(3553)
                && OpenGlBackendHolder.backend.isCapabilityEnabled(2848)
                && !BufferedGuiRenderPrimitives.capabilityState.depthWriteEnabled
                && OpenGlBackendHolder.backend.line == 2.25f && RenderUtil.matrices == 0,
                name + " restores render state and matrix");
        check(Minecraft.renderer.starts == Minecraft.renderer.ends, name + " balances renderer setup");
    }
    private static final class Pos implements StorageClusterFilter.Position {
        final int x, y, z;
        Pos(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
    }
    private static void testClusters() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 80; ++iteration) {
            List<Pos> positions = new ArrayList<Pos>();
            int size = 1 + random.nextInt(80), radius = 1 + random.nextInt(12), min = 1 + random.nextInt(8);
            for (int i = 0; i < size; ++i) positions.add(new Pos(random.nextInt(61)-30, random.nextInt(21)-10, random.nextInt(61)-30));
            List<Pos> expected = new ArrayList<Pos>();
            for (Pos p : positions) {
                int count = 0;
                for (Pos q : positions) {
                    long dx=(long)p.x-q.x, dy=(long)p.y-q.y, dz=(long)p.z-q.z;
                    if (dx*dx+dy*dy+dz*dz <= radius*radius) ++count;
                }
                if (count >= min) expected.add(p);
            }
            check(StorageClusterFilter.filter(positions, radius, min).equals(expected), "cluster index matches brute force at negative coordinates");
        }
        List<Pos> edge = Arrays.asList(new Pos(-1,0,0), new Pos(2,0,0), new Pos(2,3,0), new Pos(2,4,0));
        check(StorageClusterFilter.filter(edge, 3, 2).equals(edge), "inclusive 3D radius and cross-cell neighbors");
        List<Pos> extremes = Arrays.asList(new Pos(Integer.MIN_VALUE,0,0), new Pos(Integer.MAX_VALUE,0,0));
        check(StorageClusterFilter.filter(extremes, 1, 2).isEmpty(), "extreme coordinate wrap cannot create false clusters");
    }
    private static void testStorage(EventRender3D event) throws Exception {
        ForgeVersion.version = 110;
        Minecraft.world = new WorldClient();
        Minecraft.player = new EntityPlayerSP();
        WorldClient world = Minecraft.world;
        TileObjects.Chest chest = new TileObjects.Chest(0,0,0); chest.openness=0.5f;
        TileObjects.Trapped trapped = new TileObjects.Trapped(3,0,0);
        TileObjects.Generic barrel = new TileObjects.Generic(20,0,0);
        TileObjects.Generic blast = new TileObjects.Generic(24,0,0);
        TileObjects.Generic smoker = new TileObjects.Generic(25,0,0);
        TileObjects.Chest far = new TileObjects.Chest(900,0,0);
        TileObjects.Ender ender = new TileObjects.Ender(10,0,0); ender.openness=0.8f;
        TileObjects.Dropper dropper = new TileObjects.Dropper(12,0,0);
        TileObjects.Dispenser dispenser = new TileObjects.Dispenser(13,0,0);
        TileObjects.Shulker shulker = new TileObjects.Shulker(18,0,0);
        TileObjects.Chest broken = new TileObjects.Chest(1,0,0); broken.failPosition=true;
        Collections.addAll(world.tiles, chest, trapped, barrel, blast, smoker, far, ender, dropper, dispenser, shulker, broken);
        world.put(20,0,0,"barrel"); world.put(24,0,0,"blast_furnace"); world.put(25,0,0,"smoker");
        StorageESP storage = new StorageESP(); storage.onEnable();
        resetState(); RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.size() == 3, "default targets are regular, trapped and barrel within range");
        check(boxAt(0).U.equals(new Color(1,255,146,100)) && boxAt(0) instanceof HSBAData,
                "regular chest retains its color and open outline");
        check(boxAt(3).U.equals(new Color(255,0,0,100)), "trapped subtype has its own color");
        check(boxAt(20).U.equals(new Color(255,185,80,100)), "barrel identified via block registry");
        check(boxAt(900)==null, "out-of-range and malformed entities are skipped");
        checkState("storage");
        number(storage,"range",Double.NaN); RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(900)==null, "invalid stored storage range falls back to a bounded default");
        number(storage,"range",512);

        number(storage,"clusterMinimum",2); number(storage,"clusterRadius",3);
        RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.size()==2 && boxAt(0)!=null && boxAt(3)!=null,
                "cluster threshold counts nearby enabled blocks including the target");
        flag(storage,"renderTrappedChests",false);
        RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.isEmpty(), "disabled storage does not count toward clusters");
        flag(storage,"renderTrappedChests",true); number(storage,"clusterMinimum",1);
        flag(storage,"renderDispenser",true); flag(storage,"renderDropper",false);
        RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(13)!=null && boxAt(12)==null, "disabled dropper is not misclassified as its dispenser superclass");
        flag(storage,"renderDropper",true); flag(storage,"renderFurnace",true); flag(storage,"renderShulker",true);
        flag(storage,"renderEnderchests",true); flag(storage,"outlineOpen",false); flag(storage,"showCoordinates",true);
        RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(12)!=null && boxAt(18)!=null && boxAt(24)!=null && boxAt(25)!=null,
                "dropper, shulker, blast furnace and smoker can be enabled");
        check(!(boxAt(0) instanceof HSBAData) && !(boxAt(10) instanceof HSBAData), "outline setting applies to both chest kinds");
        check(RenderUtil.labels.contains("20, 0, 0") && RenderUtil.labels.size()==RenderUtil.boxes.size(), "coordinates label each visible block");
        number(storage,"range",3);
        RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.size()==1 && boxAt(0)!=null, "storage range uses a 3D distance to the block center");
        checkState("storage coordinates");
        setDivergentBufferedState(); RenderUtil.reset(); storage.onRender3D(event);
        checkDivergentBufferedState("storage labels");
        RenderUtil.reset(); RenderUtil.failLabel=true;
        try { storage.onRender3D(event); throw new AssertionError("expected label exception"); }
        catch (IllegalStateException expected) { checkDivergentBufferedState("storage label failure"); }
        RenderUtil.reset(); RenderUtil.failCamera=true;
        try { storage.onRender3D(event); throw new AssertionError("expected camera exception"); }
        catch (IllegalStateException expected) { checkDivergentBufferedState("storage camera failure"); }
        resetState(); RenderUtil.reset(); RenderUtil.failBox=true;
        try { storage.onRender3D(event); throw new AssertionError("expected renderer exception"); }
        catch (IllegalStateException expected) { checkState("storage exception"); }
        RenderUtil.reset(); Minecraft.world = new WorldClient(); storage.onRender3D(event);
        check(RenderUtil.boxes.isEmpty(), "storage does not retain old-world results");

        // Legacy cached type can be uninitialized or wrong; actual block wins.
        ForgeVersion.version=23; Minecraft.world=new WorldClient();
        TileObjects.Chest legacyChest=new TileObjects.Chest(-2,0,0); legacyChest.chestType=1;
        TileObjects.Chest legacyTrap=new TileObjects.Chest(-3,0,0); legacyTrap.chestType=0;
        Collections.addAll(Minecraft.world.tiles,legacyChest,legacyTrap);
        Minecraft.world.put(-2,0,0,"chest"); Minecraft.world.put(-3,0,0,"trapped_chest");
        storage.onEnable(); number(storage,"range",50); flag(storage,"showCoordinates",false);
        RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(-2).U.equals(new Color(1,255,146,100)) && boxAt(-3).U.equals(new Color(255,0,0,100)),
                "legacy trapped chest classification uses block type rather than stale cached type");
        GuiRenderPrimitives.buffered=false;
        flag(storage,"showCoordinates",true);
        GL11.glEnable(2884); GL11.glEnable(2896); GL11.glDisable(3553);
        GL11.glEnable(2929); GL11.glDisable(3042); GL11.glDepthMask(false);
        GL14.glBlendFuncSeparate(1,0,770,771);
        RenderUtil.reset(); storage.onRender3D(event);
        check(GL11.glIsEnabled(2884) && GL11.glIsEnabled(2896) && !GL11.glIsEnabled(3553)
                        && GL11.glIsEnabled(2929) && !GL11.glIsEnabled(3042) && !GL11.depthWrite,
                "legacy label restores lighting, texture, cull and depth state");
        check(GL11.srcRgb==1 && GL11.dstRgb==0 && GL11.srcAlpha==770 && GL11.dstAlpha==771,
                "legacy label restores separate blend factors");
        check(RenderUtil.matrices==0 && Minecraft.renderer.starts==Minecraft.renderer.ends,
                "legacy label balances its matrix and lightmap");

        ForgeVersion.version=110; resetState(); flag(storage,"showCoordinates",false);
        Object barrelHandle=Block.registry.get("barrel");
        Minecraft.world=new WorldClient();
        Minecraft.world.tiles.add(new TileObjects.Generic(1,0,0)); Minecraft.world.put(1,0,0,"barrel");
        Block.registry.remove("barrel"); storage.onEnable();
        RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.isEmpty(), "unavailable registry does not invent a barrel");
        Block.registry.put("barrel",barrelHandle);
        RenderUtil.reset(); storage.onRender3D(event);
        check(RenderUtil.boxes.isEmpty(), "failed registry lookup is rate-limited between retries");
        Field retry=StorageESP.class.getDeclaredField("nextBlockResolveAt"); retry.setAccessible(true); retry.setLong(storage,0L);
        RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(1)!=null, "barrel is found after a transient registry failure");
        Block.registry.put("barrel",new Object());
        Minecraft.world=new WorldClient();
        Minecraft.world.tiles.add(new TileObjects.Generic(2,0,0)); Minecraft.world.put(2,0,0,"barrel");
        RenderUtil.reset(); storage.onRender3D(event);
        check(boxAt(2)!=null, "block type cache is refreshed when the world changes");
        Block.registry.put("barrel",barrelHandle);
    }
    private static void testSpawners(EventRender3D event) throws Exception {
        ForgeVersion.version=110; Minecraft.world=new WorldClient(); Minecraft.player=new EntityPlayerSP();
        TileObjects.Spawner zombie=new TileObjects.Spawner(1,0,0,"Zombie");
        TileObjects.Spawner unknown=new TileObjects.Spawner(2,0,0,"");
        TileObjects.Spawner missing=new TileObjects.Spawner(3,0,0,null); missing.failName=true;
        TileObjects.Spawner villager=new TileObjects.Spawner(4,0,0,"Aldeano zombi");
        TileObjects.Spawner other=new TileObjects.Spawner(5,0,0,"Cow");
        TileObjects.Spawner far=new TileObjects.Spawner(900,0,0,"Zombie");
        Collections.addAll(Minecraft.world.tiles,zombie,unknown,missing,villager,other,far);
        SpawnerFinder spawner=new SpawnerFinder(); resetState(); RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.size()==3, "default whitelist plus unknown spawners, with range filtering");
        check(RenderUtil.labels.get(1).contains("Unknown Spawner") && RenderUtil.labels.get(2).contains("Unknown Spawner"),
                "missing names and name-mapping failures are visible as unknown");
        checkState("spawner");
        number(spawner,"range",Double.NaN); RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.size()==3, "invalid stored spawner range falls back to a bounded default");
        number(spawner,"range",512);
        flag(spawner,"showUnknown",false); flag(spawner,"showCoordinates",true);
        OptionalLimitValue whitelist=(OptionalLimitValue)field(spawner,"spawnerWhitelist");
        whitelist.values.clear(); whitelist.values.add("minecraft:zombie_villager");
        RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.size()==1 && RenderUtil.labels.get(0).contains("Aldeano zombi Spawner (4, 0, 0)"),
                "namespaced multiword whitelist matches a localized mob name and displays coordinates");
        whitelist.disabled.add("minecraft:zombie_villager"); RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.isEmpty(), "a nonempty list with all entries disabled matches no known mobs");
        whitelist.values.clear(); RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.size()==3, "empty whitelist shows all known spawners, not unknown when disabled");
        number(spawner,"range",2); RenderUtil.reset(); spawner.onRender3D(event);
        check(RenderUtil.labels.size()==1 && RenderUtil.labels.get(0).contains("Zombie"), "spawner range updates immediately");
        setDivergentBufferedState(); RenderUtil.reset(); spawner.onRender3D(event);
        checkDivergentBufferedState("spawner label");
        RenderUtil.reset(); RenderUtil.failLabel=true;
        try { spawner.onRender3D(event); throw new AssertionError("expected label exception"); }
        catch (IllegalStateException expected) { checkDivergentBufferedState("spawner label failure"); }
        RenderUtil.reset(); RenderUtil.failCamera=true;
        try { spawner.onRender3D(event); throw new AssertionError("expected camera exception"); }
        catch (IllegalStateException expected) { checkDivergentBufferedState("spawner camera failure"); }
        resetState(); RenderUtil.reset(); RenderUtil.failLabel=true;
        try { spawner.onRender3D(event); throw new AssertionError("expected label exception"); }
        catch (IllegalStateException expected) { checkState("spawner exception"); }
        RenderUtil.reset(); Minecraft.world=new WorldClient(); spawner.onRender3D(event);
        check(RenderUtil.labels.isEmpty(), "spawner finder does not retain old-world results");
        check(SpawnerNameMatcher.matches("entity.minecraft.zombie_villager", false,
                Arrays.asList("Zombie Villager"), key -> key), "description keys and spaced names normalize consistently");
        check(SpawnerNameMatcher.matches("mymod:evil_mob", false, Arrays.asList("evil mob"), key -> key),
                "custom namespace and spaces can match");
        check(SpawnerNameMatcher.matches("Skeleton", false, Arrays.asList("Zombie", "Skeleton"), key -> {throw new IllegalStateException();}),
                "literal whitelist matches remain usable when localization fails");
        check(!SpawnerNameMatcher.matches("Cow", false, Arrays.asList("Zombie"), key -> {throw new IllegalStateException();}),
                "unavailable translations do not crash or invent matches");
        check(!SpawnerNameMatcher.matches("", true, Collections.<String>emptyList(), key -> key), "unknown name is separate from whitelist matching");
    }
    public static void main(String[] args) throws Exception {
        for (String name : Arrays.asList("chest","trapped_chest","barrel","blast_furnace","smoker")) Block.registry.put(name,new Object());
        testClusters();
        EventRender3D event=new EventRender3D();
        testStorage(event); testSpawners(event);
        System.out.println("Base hunting modules: " + checks + " headless integration assertions passed");
    }
}
