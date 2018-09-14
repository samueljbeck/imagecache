package com.samuelbeck.diskcache;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES10;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.samuelbeck.diskcach.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by samuelbeck on 6/29/15.
 *
 * edited from com.jakewharton.disklrucache
 */
public class DiskLruImageCache {

    private DiskLruCache mDiskCache;
    private Bitmap.CompressFormat mCompressFormat = Bitmap.CompressFormat.PNG;
    private int mCompressQuality = 70;
    private static final int APP_VERSION = 1;
    private static final int VALUE_COUNT = 1;
    private static final String TAG = "DiskLruImageCache";

    public static final int IO_BUFFER_SIZE = 8 * 1024;
    private HashMap<Integer, String> filenames;



    public DiskLruImageCache(Context context, String uniqueName, int diskCacheSize, Bitmap.CompressFormat compressFormat, int quality ) {

        try {
            final File diskCacheDir = getDiskCacheDir(context, uniqueName );
            mDiskCache = DiskLruCache.open( diskCacheDir, APP_VERSION, VALUE_COUNT, diskCacheSize );
            mCompressFormat = compressFormat;
            mCompressQuality = quality;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor )
            throws IOException, FileNotFoundException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream( editor.newOutputStream( 0 ), IO_BUFFER_SIZE );
            return bitmap.compress( mCompressFormat, mCompressQuality, out );
        } finally {
            if ( out != null ) {
                out.close();
            }
        }
    }

    public void writeGifToFile(InputStream data, DiskLruCache.Editor editor) throws IOException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream( editor.newOutputStream( 0 ), IO_BUFFER_SIZE );
            byte[] buffer = new byte[2048];
            for (int n = data.read(buffer); n >= 0; n = data.read(buffer))
                out.write(buffer, 0, n);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        try {

            // Check if media is mounted or storage is built-in, if so, try and use external cache dir
            // otherwise use internal cache dir
            final String cachePath =
                    Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                            !Environment.isExternalStorageRemovable() ?
                            getExternalCacheDir(context).getPath() :
                            context.getCacheDir().getPath();

            return new File(cachePath + File.separator + uniqueName);
        }catch (Exception err) {

        }

        return null;
    }

    public Bitmap getResizedBitmap(Bitmap image, int maxImage) {
        int[] maxTextureSize = new int[1];
        GLES10 gl = new GLES10();
        gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);

        Integer maxSize = maxImage;
        if (maxTextureSize[0] > 0) {
            maxSize = maxTextureSize[0];
        }


        int width = image.getWidth();
        int height = image.getHeight();
        if (width > maxSize || height > maxSize) {
            float bitmapRatio = (float) width / (float) height;
            if (bitmapRatio > 0) {
                width = maxSize;
                height = (int) (width / bitmapRatio);
            } else {
                height = maxSize;
                width = (int) (height * bitmapRatio);
            }
            return Bitmap.createScaledBitmap(image, width, height, true);
        } else {
            return image;
        }
    }

    public static String replaceNonRegexCharacters(final String url) {
        String _url = "";
        for(Character c : url.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                _url = _url + "_";
            } else {
                _url = _url + c.toString().toLowerCase();

            }
        }
        if (_url.length() > 63) {
            _url = _url.substring(_url.length() - 63, _url.length());
        }
        return _url;


    }

    public void put(String key, Bitmap data ) {
        //data = getResizedBitmap(data, 3000);
        key = replaceNonRegexCharacters(key);
        Log.d("DiskLruCachePUT", "H=" + data.getHeight() + " W=" + data.getWidth());
        DiskLruCache.Editor editor = null;
        try {
            editor = mDiskCache.edit( key );
            if ( editor == null ) {
                return;
            }

            if( writeBitmapToFile( data, editor ) ) {
                mDiskCache.flush();
                editor.commit();
                if ( BuildConfig.DEBUG ) {
                    Log.d( "cache_test_DISK_", "image put on disk cache " + key );
                }
            } else {
                editor.abort();
                if ( BuildConfig.DEBUG ) {
                    Log.d( "cache_test_DISK_", "ERROR on: image put on disk cache " + key );
                }
            }
        } catch (IOException e) {
            if ( BuildConfig.DEBUG ) {
                Log.d( "cache_test_DISK_", "ERROR on: image put on disk cache " + key );
            }
            try {
                if ( editor != null ) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
        } catch (Exception err) {
            Log.d("DiskLruCache", err.getMessage());
        }

    }

    public Bitmap getBitmap(String key ) {
        key = replaceNonRegexCharacters(key);
        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapshot = null;
        try {

            snapshot = mDiskCache.get( key );
            if ( snapshot == null ) {
                return null;
            }
            InputStream in = snapshot.getInputStream( 0 );
            if ( in != null ) {
                BufferedInputStream buffIn = new BufferedInputStream( in, IO_BUFFER_SIZE );

                try {
                    bitmap = BitmapFactory.decodeStream(buffIn);
                } catch (OutOfMemoryError err) {
                    Log.d("Out Of Memory Error", "DiskLruImageCache");
                    return null;

                }
            }
        } catch ( IOException e ) {
            e.printStackTrace();
        } finally {
            if ( snapshot != null ) {
                snapshot.close();
            }
        }

        if ( BuildConfig.DEBUG ) {
            Log.d( "cache_test_DISK_", bitmap == null ? "" : "image read from disk " + key);
        }
        return bitmap;
    }

    public void put(String key, InputStream data) {
        key = replaceNonRegexCharacters(key);
        Log.d(TAG, "putGif w/ key: " + key);
        DiskLruCache.Editor editor = null;
        try {
            editor = mDiskCache.edit(key);
            if (editor == null) {
                return;
            }

            try {
                writeGifToFile(data, editor);
                mDiskCache.flush();
                editor.commit();

                if (BuildConfig.DEBUG) {
                    Log.d( "cache_test_DISK_", "gif put on disk cache " + key );
                }
            } catch (IOException e) {
                editor.abort();
                if (BuildConfig.DEBUG) {
                    Log.d( "cache_test_DISK_", "ERROR on: gif put on disk cache: " + e.toString() );
                }
            }
        } catch (IOException e) {
            if ( BuildConfig.DEBUG ) {
                Log.d( "cache_test_DISK_", "ERROR on: gif put on disk cache " + key );
            }
            try {
                if ( editor != null ) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
        } catch (Exception err) {
            Log.d("DiskLruCache", err.getMessage());
        }
    }

    public InputStream getGif (String key) {
        key = replaceNonRegexCharacters(key);
        Log.d(TAG, "getGif w/ key: " + key);
        DiskLruCache.Snapshot snapshot = null;

        InputStream is = null;
        try {
            snapshot = mDiskCache.get( key );
            if ( snapshot == null ) {
                return null;
            }

            is = snapshot.getInputStream( 0 );
        }

        catch ( IOException e ) {
            e.printStackTrace();
        }
//        } finally {
//            if ( snapshot != null ) {
//                snapshot.close();
//            }
//        }

        if ( BuildConfig.DEBUG ) {
            Log.d( "cache_test_DISK_", is == null ? "failed to pull gif" : "gif read from disk " + key);
        }

        return is;
    }

    public Boolean containsKey( String key ) {
        key = replaceNonRegexCharacters(key);
        boolean contained = false;
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = mDiskCache.get( key );
            contained = snapshot != null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception err) {
            Log.i("DiskLru1", err.getMessage());
        } finally {

            try {
                if (snapshot != null) {
                    snapshot.close();
                } else {

                }
            } catch (Exception err) {
                Log.i("DiskLru", err.getMessage());
            }
        }

        return contained;

    }

    public void clearCache() {
        if ( BuildConfig.DEBUG ) {
            Log.d("cache_test_DISK_", "disk cache CLEARED");
        }
        try {
            mDiskCache.delete();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }



    public long getCacheSize() {
        try {
            return mDiskCache.size();
        } catch (Exception err) {

        }
        return 0;
    }

    public long getCacheMaxSize() {
        try {

            return mDiskCache.getMaxSize();

        } catch (Exception err) {

        }
        return  0;
    }



    public File getCacheFolder() {
        return mDiskCache.getDirectory();
    }

    public static File getExternalCacheDir(Context context) {
        if (hasExternalCacheDir()) {
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    public static boolean hasExternalCacheDir() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public void remove(String key) {
        try {
            key = replaceNonRegexCharacters(key);
            mDiskCache.remove(key);
        } catch (Exception err) {

        }
    }

    public String[] getKeyList() {
        if (mDiskCache == null) {
            return new String[0];
        } else {
            return mDiskCache.getKeyList();
        }
    }

}