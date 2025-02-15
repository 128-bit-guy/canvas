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

package grondag.canvas.buffer.allocation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import grondag.canvas.draw.DrawableDelegate;
import grondag.canvas.varia.GLBufferStore;

public abstract class UploadableBuffer extends AbstractBuffer implements BindableBuffer {
    protected static int nextID = 0;

    private int glBufferId = -1;

    public final int id = nextID++;

    /**
     * Count of delegates currently using the buffer for rendering.
     */
    protected final AtomicInteger retainers = new AtomicInteger();

    @Override
    public BindableBuffer bindable() {
        return this;
    }
    
    @Override
    public int glBufferId() {
        int result = glBufferId;
        if(result == -1) {
            result = GLBufferStore.claimBuffer();
            glBufferId = result;
        }
        return result;
    }
    
    @Override
    public boolean bind() {
        return BindStateManager.bind(this.glBufferId());
    }

    @Override
    public void unbind() {
        BindStateManager.unbind();
    }

    /** called before chunk populates int buffer(). May be called off thread */
    @Override
    public final void lockForWrite() {
//        buffer.bufferLock.lock();
    }

    /** called after chunk populates int buffer(). May be called off thread */
    @Override
    public final void unlockForWrite() {
//        buffer.bufferLock.unlock();
    }
    
    @Override
    public void retain(DrawableDelegate drawable) {
        retainers.getAndIncrement();
    }

    /**
     * Signals the buffer will no longer be used. May be called off-thread.
     */
    @Override
    public void release(DrawableDelegate drawable) {
        if(retainers.decrementAndGet() == 0) {
            dispose();
        }
    }

    private final AtomicBoolean isDisposed = new AtomicBoolean();

    @Override
    public final boolean isDisposed() {
        return isDisposed.get();
    }

    /** called by store on render reload to recycle GL buffer */
    protected final void dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            if(glBufferId != -1) {
                GLBufferStore.releaseBuffer(glBufferId);
                glBufferId = -1;
            }
            onDispose();
        }
    }
    
    protected abstract void onDispose();
}
