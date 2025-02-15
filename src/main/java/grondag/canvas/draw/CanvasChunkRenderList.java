/*******************************************************************************
 * Copyright 2019 grondag
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package grondag.canvas.draw;

import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;

import grondag.canvas.CanvasMod;
import grondag.canvas.Configurator;
import grondag.canvas.buffer.packing.RenderCube;
import grondag.canvas.chunk.ChunkRendererExt;
import grondag.canvas.chunk.DrawableChunk;
import grondag.canvas.light.LightmapHdTexture;
import grondag.canvas.material.ShaderContext;
import grondag.canvas.varia.CanvasGlHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.block.BlockRenderLayer;
import net.minecraft.client.render.chunk.ChunkRenderer;
import net.minecraft.util.math.BlockPos;

public class CanvasChunkRenderList {
    protected final ObjectArrayList<ChunkRenderer> chunks = new ObjectArrayList<ChunkRenderer>();

    /**
     * Each entry is for a single 256^3 render cube.<br>
     * The entry is an array of CompoundVertexBuffers.<br>
     * The array is as long as the highest pipeline index.<br>
     * Null values mean that pipeline isn't part of the render cube.<br>
     * Non-null values are lists of buffer in that cube with the given pipeline.<br>
     */
    private final Long2ObjectOpenHashMap<SolidRenderList> solidLists = new Long2ObjectOpenHashMap<>();

    /**
     * Will hold the modelViewMatrix that was in GL context before first call to
     * block render layer this pass.
     */
    protected final Matrix4f mvMatrix = new Matrix4f();
    protected final Matrix4f mvPos = new Matrix4f();
    protected final Matrix4f xlatMatrix = new Matrix4f();

    protected final FloatBuffer modelViewMatrixBuffer = BufferUtils.createFloatBuffer(16);

    private int originX = Integer.MIN_VALUE;
    private int originY = Integer.MIN_VALUE;
    private int originZ = Integer.MIN_VALUE;
    private boolean didUpdateTransform = false;
    
    private double viewEntityX;
    private double viewEntityY;
    private double viewEntityZ;

    public CanvasChunkRenderList() {
        xlatMatrix.identity();
    }

    public void initialize(double viewEntityXIn, double viewEntityYIn, double viewEntityZIn) {
        this.viewEntityX = viewEntityXIn;
        this.viewEntityY = viewEntityYIn;
        this.viewEntityZ = viewEntityZIn;
    }

    public void addChunkRenderer(ChunkRenderer renderChunkIn, BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT)
            this.chunks.add(renderChunkIn);
        else
            this.addSolidChunk(renderChunkIn);
    }

    private void addSolidChunk(ChunkRenderer renderChunkIn) {
        final long cubeKey = RenderCube.getPackedOrigin(renderChunkIn.getOrigin());

        SolidRenderList solidList = solidLists.get(cubeKey);
        if (solidList == null) {
            solidList = SolidRenderList.claim();
            solidLists.put(cubeKey, solidList);
        }
        addSolidChunkInner(renderChunkIn, solidList);
    }

    private void addSolidChunkInner(ChunkRenderer renderChunkIn, SolidRenderList solidList) {
        final DrawableChunk.Solid solidDrawable = ((ChunkRendererExt) renderChunkIn).canvas_solidDrawable();
        if (solidDrawable != null)
            solidDrawable.prepareSolidRender(solidList);
    }

    public void renderChunkLayer(BlockRenderLayer layer) {
        LightmapHdTexture.instance().enable();
        if (layer == BlockRenderLayer.SOLID)
            renderChunkLayerSolid();
        else
            renderChunkLayerTranslucent();
        LightmapHdTexture.instance().disable();
    }

    private final void updateViewMatrix(long packedRenderCubeKey) {
        updateViewMatrix(RenderCube.getPackedKeyOriginX(packedRenderCubeKey),
                RenderCube.getPackedKeyOriginY(packedRenderCubeKey),
                RenderCube.getPackedKeyOriginZ(packedRenderCubeKey));
    }

    private final void updateViewMatrix(BlockPos renderChunkOrigin) {
        updateViewMatrix(RenderCube.renderCubeOrigin(renderChunkOrigin.getX()),
                RenderCube.renderCubeOrigin(renderChunkOrigin.getY()),
                RenderCube.renderCubeOrigin(renderChunkOrigin.getZ()));
    }

    private final void updateViewMatrix(final int ox, final int oy, final int oz) {
        if (ox == originX && oz == originZ && oy == originY && didUpdateTransform) {
           return;
        };
        
        if(didUpdateTransform ) {
            GlStateManager.popMatrix();
        } else {
            didUpdateTransform = true;
        }
        
        GlStateManager.pushMatrix();
        originX = ox;
        originY = oy;
        originZ = oz;
        updateViewMatrixInner(ox, oy, oz);
    }

    private final void updateViewMatrixInner(final int ox, final int oy, final int oz) {
        GlStateManager.translatef((float) (ox - viewEntityX), (float) (oy - viewEntityY), (float) (oz - viewEntityZ));

        // vanilla applies a per-chunk scaling matrix, but does not seem to be essential
        // - probably a hack to prevent seams/holes due to FP error
        // If really is necessary, would want to handle some other way. Per-chunk matrix
        // not initialized when Acuity enabled.
//        Matrix4f.mul(mvChunk, mvPos, mvPos);
    }

    private final void preRenderSetup() {
        originX = Integer.MIN_VALUE;
        originZ = Integer.MIN_VALUE;

        // NB: Vanilla MC will have already enabled GL_VERTEX_ARRAY, GL_COLOR_ARRAY
        // and GL_TEXTURE_COORD_ARRAY for both default texture and lightmap.
        // We don't use these except for GL_VERTEX so disable them now unless we
        // are using VAOs, which will cause them to be ignored.
        // Not a problem to disable them because MC disables the when we return.
        if (!CanvasGlHelper.isVaoEnabled()) {
            disableUnusedAttributes();
        }
    }

    private final void disableUnusedAttributes() {
        GlStateManager.disableClientState(GL11.GL_COLOR_ARRAY);
        GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
        GlStateManager.disableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GLX.glClientActiveTexture(GLX.GL_TEXTURE1);
        GlStateManager.disableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
    }

    public final void downloadModelViewMatrix() {
        final FloatBuffer modelViewMatrixBuffer = this.modelViewMatrixBuffer;
        modelViewMatrixBuffer.position(0);
        GlStateManager.getMatrix(GL11.GL_MODELVIEW_MATRIX, modelViewMatrixBuffer);
        mvMatrix.set(modelViewMatrixBuffer);
    }

    protected final void renderChunkLayerSolid() {
        if (this.solidLists.isEmpty())
            return;

        if(Configurator.logGlStateChanges) {
            CanvasMod.LOG.info(String.format("GlState: renderChunkLayerSolid for %d lists", solidLists.size()));
        }
        
        preRenderSetup();

        ObjectIterator<Entry<SolidRenderList>> it = solidLists.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Entry<SolidRenderList> e = it.next();
            updateViewMatrix(e.getLongKey());
            e.getValue().drawAndRelease(ShaderContext.BLOCK_SOLID);
        }

        solidLists.clear();
        postRenderCleanup();
    }

    protected final void renderChunkLayerTranslucent() {
        final ObjectArrayList<ChunkRenderer> chunks = this.chunks;
        final int chunkCount = chunks.size();

        if (chunkCount == 0)
            return;
        
        if(Configurator.logGlStateChanges) {
            CanvasMod.LOG.info(String.format("GlState: renderChunkLayerTranslucent for %d chunks", chunkCount));
        }
        
        preRenderSetup();

        for (int i = 0; i < chunkCount; i++) {
            final ChunkRenderer renderchunk = chunks.get(i);
            final DrawableChunk.Translucent drawable = ((ChunkRendererExt) renderchunk).canvas_translucentDrawable();
            if (drawable == null)
                continue;
            updateViewMatrix(renderchunk.getOrigin());
            drawable.renderChunkTranslucent();
        }

        chunks.clear();
        postRenderCleanup();
    }

    private final void postRenderCleanup() {
        if(didUpdateTransform ) {
            GlStateManager.popMatrix();
            didUpdateTransform = false;
        }
        SolidRenderList.postDrawCleanup();
        GlStateManager.clearCurrentColor();
    }
}
