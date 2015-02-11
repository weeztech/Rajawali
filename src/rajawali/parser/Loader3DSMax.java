/**
 * Copyright 2013 Dennis Ippel
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package rajawali.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import rajawali.Object3D;
import rajawali.materials.Material;
import rajawali.materials.methods.DiffuseMethod;
import rajawali.materials.textures.ATexture.TextureException;
import rajawali.math.vector.Vector3;
import rajawali.renderer.RajawaliRenderer;
import rajawali.util.RajLog;

/**
 * 3DS object parser. This is a work in progress. Materials aren't parsed yet.
 *
 * @author dennis.ippel
 * @author lacasrac
 *
 */
public class Loader3DSMax extends AMeshLoader {
    
    private final int IDENTIFIER_3DS = 0x4D4D;
    private final int MESH_BLOCK = 0x3D3D;
    private final int OBJECT_BLOCK = 0x4000;
    private final int TRIMESH = 0x4100;
    private final int VERTICES = 0x4110;
    private final int FACES = 0x4120;
    private final int TEXCOORD = 0x4140;
    private final int TEX_MAP = 0xA200;
    private final int TRI_MATERIAL = 0x4130;
    private final int TEX_NAME = 0xA000;
    private final int TEX_FILENAME = 0xA300;
    private final int MATERIAL = 0xAFFF;
    
    private ArrayList<float[]> mVertices = new ArrayList<float[]>();
    // private ArrayList<float[]> mNormals = new ArrayList<float[]>();
    private ArrayList<float[]> mVertNormals = new ArrayList<float[]>();
    private ArrayList<float[]> mTexCoords = new ArrayList<float[]>();
    private ArrayList<int[]> mIndices = new ArrayList<int[]>();
    private ArrayList<String> mObjNames = new ArrayList<String>();
    
    private int mChunkID;
    private int mChunkEndOffset;
    private boolean mEndReached = false;
    private int mObjects = -1;
    
    public Loader3DSMax(RajawaliRenderer renderer, int resourceID) {
        super(renderer.getContext().getResources(), renderer.getTextureManager(), resourceID);
    }
    
    public Loader3DSMax(RajawaliRenderer renderer, File file) {
        super(renderer, file);
    }
    
    @Override
    public AMeshLoader parse() throws ParsingException {
        RajLog.i("Start parsing 3DS");
        
        final InputStream stream;
        if (mFile == null) {
            stream = new BufferedInputStream(mResources.openRawResource(mResourceId));
        } else {
            try {
                stream = new BufferedInputStream(new FileInputStream(mFile));
            } catch (Exception e) {
                throw new ParsingException(e);
            }
        }
        
        try {
            readHeader(stream);
            if (mChunkID != IDENTIFIER_3DS) {
                RajLog.e("Not a valid 3DS file");
                return null;
            }
            
            while (!mEndReached) {
                readChunk(stream);
            }
            
            try {
                build();
            } catch (TextureException tme) {
                throw new ParsingException(tme);
            }
            if (mRootObject.getNumChildren() == 1)
                mRootObject = mRootObject.getChildAt(0);
            
            stream.close();
            
            RajLog.i("End parsing 3DS");
        } catch (IOException e) {
            RajLog.e("Error parsing");
            throw new ParsingException(e);
        }
        
        return this;
    }
    
    void readChunk(InputStream stream) throws IOException {
        readHeader(stream);
        
        switch (mChunkID) {
            case MESH_BLOCK:
                break;
            case OBJECT_BLOCK:
                mObjects++;
                mObjNames.add(readString(stream));
                break;
            case TRIMESH:
                break;
            case VERTICES:
                readVertices(stream);
                break;
            case FACES:
                readFaces(stream);
                break;
            case TEXCOORD:
                readTexCoords(stream);
                break;
            case TEX_NAME:
                // mCurrentMaterialKey = readString(stream);
                skipRead(stream);
                break;
            case TEX_FILENAME:
                // String fileName = readString(stream);
                // StringBuffer texture = new StringBuffer(packageID);
                // texture.append(":drawable/");
                //
                // StringBuffer textureName = new StringBuffer(fileName.toLowerCase());
                // int dotIndex = textureName.lastIndexOf(".");
                // if (dotIndex > -1)
                // texture.append(textureName.substring(0, dotIndex));
                // else
                // texture.append(textureName);
                //
                // textureAtlas.addBitmapAsset(new BitmapAsset(mCurrentMaterialKey, texture.toString()));
                skipRead(stream);
                break;
            case TRI_MATERIAL:
                // String materialName = readString(stream);
                // int numFaces = readShort(stream);
                //
                // for (int i = 0; i < numFaces; i++) {
                // int faceIndex = readShort(stream);
                // co.faces.get(faceIndex).materialKey = materialName;
                // }
                skipRead(stream);
                break;
            case MATERIAL:
                break;
            case TEX_MAP:
                break;
            default:
                skipRead(stream);
        }
    }
    
    public void build() throws TextureException {
        int num = mVertices.size();
        for (int j = 0; j < num; ++j) {
            final int[] indices = mIndices.get(j);
            final float[] vertices = mVertices.get(j);
            final float[] texCoords = mTexCoords.size() > j ? mTexCoords.get(j) : new float[vertices.length / 3 * 2];
            final float[] vertNormals = mVertNormals.get(j);
            
            Object3D targetObj = new Object3D(mObjNames.get(j));
            targetObj.setData(vertices, vertNormals, texCoords, null, indices);
            // -- diffuse material with random color. for now.
            Material material = new Material();
            material.setDiffuseMethod(new DiffuseMethod.Lambert());
            targetObj.setMaterial(material);
            targetObj.setColor(0xff000000 + (int) (Math.random() * 0xffffff));
            mRootObject.addChild(targetObj);
        }
    }
    
    public void clear() {
        mIndices.clear();
        mVertNormals.clear();
        mVertices.clear();
        mTexCoords.clear();
    }
    
    protected void skipRead(InputStream stream) throws IOException {
        for (int i = 0; (i < mChunkEndOffset - 6) && !mEndReached; i++) {
            mEndReached = stream.read() < 0;
        }
    }
    
    private void readFloats(InputStream stream, float[] floats) throws IOException {
        final byte[] buffer = new byte[1024*16];
        int rest = floats.length * 4;
        int floatPos = 0;
        int r;
        do {
            int bufPos = 0;
            for (;;) {
                r = stream.read(buffer, bufPos, Math.min(rest, buffer.length - bufPos));
                if (r > 0) {
                    bufPos += r;
                    rest -= r;
                    if (rest != 0 && bufPos != buffer.length) {
                        continue;
                    }
                }
                break;
            }
            for (int i = 0; i < bufPos;) {
                final int intV = (buffer[i++] & 0xFF) | ((buffer[i++] & 0xFF) << 8) | ((buffer[i++] & 0xFF) << 16)
                | ((buffer[i++] & 0xFF) << 24);
                floats[floatPos++] = Float.intBitsToFloat(intV);
            }
        } while (r >= 0 && rest != 0);
        
    }
    
    protected void readVertices(InputStream buffer) throws IOException {
        final int numVertices = readShort(buffer) * 3;
        final float[] vertices = new float[numVertices];
        
        readFloats(buffer, vertices);
        
        mVertices.add(vertices);
    }
    
    protected void readTexCoords(InputStream buffer) throws IOException {
        final int numVertices = readShort(buffer) * 2;
        final float[] texCoords = new float[numVertices];
        
        readFloats(buffer, texCoords);
        for (int i = 1; i < numVertices; i += 2) {
            texCoords[i] = 1 - texCoords[i];
        }
        
        mTexCoords.add(texCoords);
    }
    
    protected void readFaces(InputStream stream) throws IOException {
        final int triangles = readShort(stream) * 3;
        final int numIndices = triangles;
        final int[] indices = new int[numIndices];
        final byte[] buffer = new byte[1024*16];
        
        final float[] vertices = mVertices.get(mObjects);
        final int numVertices = vertices.length;
        
        final Vector3 v2sub1 = new Vector3();
        final Vector3 v3sub2 = new Vector3();
        final Vector3 normal = new Vector3();
        float[] vertNormals = new float[numVertices];
        
        int rest = triangles / 3 * 8;
        int indexPos = 0;
        int r;
        do {
            int bufPos = 0;
            for (;;) {
                r = stream.read(buffer, bufPos, Math.min(rest, buffer.length - bufPos));
                if (r > 0) {
                    bufPos += r;
                    rest -= r;
                    if (rest != 0 && bufPos != buffer.length) {
                        continue;
                    }
                }
                break;
            }
            for (int i = 0; i < bufPos; i += 2) {
                final int v1x = (indices[indexPos++] = (buffer[i++] & 0xFF) | ((buffer[i++] & 0xFF) << 8)) * 3;
                final int v1y = v1x + 1;
                final int v1z = v1y + 1;
                final int v2x = (indices[indexPos++] = (buffer[i++] & 0xFF) | ((buffer[i++] & 0xFF) << 8)) * 3;
                final int v2y = v2x + 1;
                final int v2z = v2y + 1;
                final int v3x = (indices[indexPos++] = (buffer[i++] & 0xFF) | ((buffer[i++] & 0xFF) << 8)) * 3;
                final int v3y = v3x + 1;
                final int v3z = v3y + 1;
                v2sub1.setAll(vertices[v3x], vertices[v3y], vertices[v3z]).subtract(vertices[v1x],
                                                                                    vertices[v1y], vertices[v1z]);
                v3sub2.setAll(vertices[v2x], vertices[v2y], vertices[v2z]).subtract(vertices[v1x],
                                                                                    vertices[v1y], vertices[v1z]);
                normal.crossAndSet(v2sub1, v3sub2);
                normal.normalize();
                vertNormals[v1x] += normal.x;
                vertNormals[v1y] += normal.y;
                vertNormals[v1z] += normal.z;
                vertNormals[v2x] += normal.x;
                vertNormals[v2y] += normal.y;
                vertNormals[v2z] += normal.z;
                vertNormals[v3x] += normal.x;
                vertNormals[v3y] += normal.y;
                vertNormals[v3z] += normal.z;
            }
        } while (r >= 0 && rest != 0);
        
        mIndices.add(indices);
        
        for (int i = 0; i < numVertices; i += 3) {
            double x = vertNormals[i];
            double y = vertNormals[i + 1];
            double z = vertNormals[i + 2];
            double mod = Math.sqrt(x * x + y * y + z * z);
            if (mod != 0 && mod != 1) {
                mod = 1 / mod;
                vertNormals[i] *= mod;
                vertNormals[i + 1] *= mod;
                vertNormals[i + 2] *= mod;
            }
        }
        mVertNormals.add(vertNormals);
    }
    
    protected void readHeader(InputStream stream) throws IOException {
        mChunkID = readShort(stream);
        mChunkEndOffset = readInt(stream);
        mEndReached = mChunkID < 0;
    }
    
    protected String readString(InputStream stream) throws IOException {
        String result = new String();
        byte inByte;
        while ((inByte = (byte) stream.read()) != 0)
            result += (char) inByte;
        return result;
    }
    
    protected int readInt(InputStream stream) throws IOException {
        return stream.read() | (stream.read() << 8) | (stream.read() << 16) | (stream.read() << 24);
    }
    
    protected int readShort(InputStream stream) throws IOException {
        return (stream.read() | (stream.read() << 8));
    }
    
    protected float readFloat(InputStream stream) throws IOException {
        return Float.intBitsToFloat(readInt(stream));
    }
    
}