package gg.vape.module.render.entity;

import gg.vape.module.render.entity.RenderEntityContext;
import gg.vape.utils.render.RenderUtil;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import java.awt.Color;

public class ProjectedEntityBounds {
    public double minX = -100.0;
    public double minY = -100.0;
    public double maxY = -100.0;
    public double maxX = -100.0;
    public final Entity entity;
    public final Color color;
    public final RenderEntityContext context;
    public final boolean onScreen;


    public ProjectedEntityBounds(double offsetX, double offsetY, double offsetZ, AxisAlignedBB axisAlignedBB, Entity entity, RenderEntityContext renderEntityContext, Color color) {
        this.entity = entity;
        this.context = renderEntityContext;
        this.color = color;
        double[] corner0 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMinX(), offsetY + axisAlignedBB.getMinY(), offsetZ + axisAlignedBB.getMinZ());
        double[] corner1 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMaxX(), offsetY + axisAlignedBB.getMinY(), offsetZ + axisAlignedBB.getMinZ());
        double[] corner2 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMaxX(), offsetY + axisAlignedBB.getMinY(), offsetZ + axisAlignedBB.getMaxZ());
        double[] corner3 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMinX(), offsetY + axisAlignedBB.getMinY(), offsetZ + axisAlignedBB.getMaxZ());
        double[] corner4 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMinX(), offsetY + axisAlignedBB.getMaxY(), offsetZ + axisAlignedBB.getMinZ());
        double[] corner5 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMaxX(), offsetY + axisAlignedBB.getMaxY(), offsetZ + axisAlignedBB.getMinZ());
        double[] corner6 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMaxX(), offsetY + axisAlignedBB.getMaxY(), offsetZ + axisAlignedBB.getMaxZ());
        double[] corner7 = RenderUtil.WChecked(offsetX + axisAlignedBB.getMinX(), offsetY + axisAlignedBB.getMaxY(), offsetZ + axisAlignedBB.getMaxZ());
        this.onScreen = corner0 != null && corner1 != null && corner2 != null && corner3 != null
                && corner4 != null && corner5 != null && corner6 != null && corner7 != null
                && corner0[2] >= 0.0 && corner0[2] < 1.0 && corner1[2] >= 0.0 && corner1[2] < 1.0 && corner2[2] >= 0.0 && corner2[2] < 1.0 && corner3[2] >= 0.0 && corner3[2] < 1.0 && corner4[2] >= 0.0 && corner4[2] < 1.0 && corner5[2] >= 0.0 && corner5[2] < 1.0 && corner6[2] >= 0.0 && corner6[2] < 1.0 && corner7[2] >= 0.0 && corner7[2] < 1.0;
        if (!this.onScreen) {
            return;
        }
        double minX = corner0[0];
        double minY = corner0[1];
        double maxX = corner7[0];
        double maxY = corner7[1];
        double[] screenXs = new double[]{corner0[0], corner1[0], corner2[0], corner3[0], corner4[0], corner5[0], corner6[0], corner7[0]};
        double[] screenYs = new double[]{corner0[1], corner1[1], corner2[1], corner3[1], corner4[1], corner5[1], corner6[1], corner7[1]};
        for (double x : screenXs) {
            if (!(x < minX)) continue;
            minX = x;
        }
        for (double x : screenXs) {
            if (!(x > maxX)) continue;
            maxX = x;
        }
        for (double y : screenYs) {
            if (!(y < minY)) continue;
            minY = y;
        }
        for (double y : screenYs) {
            if (!(y > maxY)) continue;
            maxY = y;
        }
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }
}

