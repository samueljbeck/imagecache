# DiskCache
Fork of JakeWharton DiskLruCach.  
Needed some extra options.  See the original at https://github.com/JakeWharton/DiskLruCache


###Use
This is a disk cache used in conjunction with LruCache (https://developer.android.com/reference/android/util/LruCache). Together this maximizes the speed of LruCache while using the increased space of DiskCache.

###Setup
These are the setting I have found to work for me.

In the Application class:

    public LruCache<String, Bitmap> cache;
    public DiskLruImageCache diskCache;
    
	private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 1000; // 10000MB
    private static final Bitmap.CompressFormat DISK_COMPRESS_FORMAT = Bitmap.CompressFormat.PNG;  
    private static final int DISK_COMPRESS_QUALITY = 70;
	    
	void setUpCaches() {
        ActivityManager manager = (ActivityManager)
        getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = manager.getMemoryClass();
        int memoryClassInKilobytes = memoryClass * 1024;
        int DEFAULT_CACHE_SIZE_PROPORTION = 32;
        int cacheSize = memoryClassInKilobytes / DEFAULT_CACHE_SIZE_PROPORTION / 4;

        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
        diskCache = new DiskLruImageCache(this, DISK_CACHE_SUBDIR, DISK_CACHE_SIZE, DISK_COMPRESS_FORMAT, DISK_COMPRESS_QUALITY);
    }
    
	void doLogout() {    
		for (String key : cache.snapshot().keySet()) {  // used to remove profile pic from MainMenu
                if (key.startsWith(getUserId())) {
                    cache.remove(key);
                }
         }

         for (String key : diskCache.getKeyList()) {
                if (key.startsWith(DiskLruImageCache.replaceNonRegexCharacters(getUserId()))) {
                    diskCache.remove(key);
                }
        }
            
        diskCache.clearCache();
        cache = null;
        
	}
   

###Implementation
Saving a bmp

	if (bmp != null) {
       cache.put(tag, bmp);
       diskCache.put(tag, bmp);
    }
    

Retrieving a bmp

	Bitmap bitmap = cache.get(tag);
	if (bitmap != null) {
		//do something with the bitmap
	} else if (diskCache.containsKey(tag)) {
		Bitmap bmp = diskCache.getBitmap(tag);
		cache.put(tag, bmp); // add it back to the cache for quick retrieval
		// do something with bitmap
	}
	


