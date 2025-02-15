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

package grondag.canvas.chunk.occlusion;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import net.minecraft.util.math.Direction;

@SuppressWarnings("serial")
public class ChunkOcclusionMap extends Short2ByteOpenHashMap {
    private static ArrayBlockingQueue<ChunkOcclusionMap> occlusionMaps = new ArrayBlockingQueue<>(4096);

    public static ChunkOcclusionMap claim() {
        ChunkOcclusionMap result = occlusionMaps.poll();
        if (result == null)
            result = new ChunkOcclusionMap();
        else
            result.clear();
        return result;
    }

    public static void release(ChunkOcclusionMap map) {
        occlusionMaps.offer(map);
    }

    public Set<Direction> getFaceSet(int index) {
        return DirectionSet.sharedInstance(this.get((short) index));
    }

    public void setIndex(int positionIndex, int setIndex) {
        this.put((short) positionIndex, (byte) setIndex);
    }
}
