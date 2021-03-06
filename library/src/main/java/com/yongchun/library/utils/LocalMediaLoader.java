package com.yongchun.library.utils;

import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;

import com.yongchun.library.model.LocalMedia;
import com.yongchun.library.model.LocalMediaFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.List;

/**
 * Created by dee on 15/11/19.
 */
public class LocalMediaLoader {
    // load type
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;

    private final static String[] IMAGE_PROJECTION = {
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media._ID};

    private final static String[] VIDEO_PROJECTION = {
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DURATION};

    private int type = TYPE_IMAGE;
    private FragmentActivity activity;

    public LocalMediaLoader(FragmentActivity activity, int type) {
        this.activity = activity;
        this.type = type;
    }

    HashSet<String> mDirPaths = new HashSet<String>();

    public void loadAllImage(final LocalMediaLoadListener imageLoadListener) {
        activity.getSupportLoaderManager().initLoader(type, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                CursorLoader cursorLoader = null;
                if (id == TYPE_IMAGE) {
                    cursorLoader = new CursorLoader(
                            activity,
							MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            IMAGE_PROJECTION,
							MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?",
                            new String[]{"image/jpeg", "image/png"},
							IMAGE_PROJECTION[2] + " DESC");
                } else if (id == TYPE_VIDEO) {
					cursorLoader = new CursorLoader(
                            activity,
							MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            VIDEO_PROJECTION,
							null,
							null,
							VIDEO_PROJECTION[2] + " DESC");
                }
                return cursorLoader;
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
				if(data.isClosed()){
					return;
				}
                ArrayList<LocalMediaFolder> imageFolders = new ArrayList<LocalMediaFolder>();
                LocalMediaFolder allImageFolder = new LocalMediaFolder();
                List<LocalMedia> allImages = new ArrayList<LocalMedia>();

                while (data != null && data.moveToNext()) {
                    // 获取图片的路径
                    String path = data.getString(data
                            .getColumnIndex(MediaStore.Images.Media.DATA));
                    File file = new File(path);
                    if (!file.exists())
                        continue;
                    // 获取该图片的目录路径名
                    File parentFile = file.getParentFile();
                    if (parentFile == null || !parentFile.exists())
                        continue;

                    String dirPath = parentFile.getAbsolutePath();
                    // 利用一个HashSet防止多次扫描同一个文件夹
                    if (mDirPaths.contains(dirPath)) {
                        continue;
                    } else {
                        mDirPaths.add(dirPath);
                    }

                    if (parentFile.list() == null)
                        continue;
                    LocalMediaFolder localMediaFolder = getImageFolder(path, imageFolders);

                    File[] files = parentFile.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
							filename = filename.toLowerCase(); //why did you not do that before
							//detect mimeType
							String mimeType = null;
							try {
								mimeType = URLConnection.guessContentTypeFromName(filename);
							}catch (StringIndexOutOfBoundsException exception){
								exception.printStackTrace();
							}
							if(TextUtils.isEmpty(mimeType)){
								try {
									mimeType = URLConnection.guessContentTypeFromStream(new FileInputStream(new File(dir,filename)));
								}catch (Exception exception){
								//	exception.printStackTrace();
								}
							}
                            if(TYPE_IMAGE == type) {
								if(!TextUtils.isEmpty(mimeType) && mimeType.startsWith("image")) {
									return true;
								}
							}
							if(TYPE_VIDEO == type){
								if(!TextUtils.isEmpty(mimeType) && mimeType.startsWith("video")) {
									return true;
								}
							}
                            return false;
                        }
                    });
                    ArrayList<LocalMedia> images = new ArrayList<>();
                    for (int i = 0; i < files.length; i++) {
                        File f = files[i];
                        LocalMedia localMedia = new LocalMedia(f.getAbsolutePath());
						localMedia.setLastUpdateAt(f.lastModified());
						//get the video duration;
						if(type == TYPE_VIDEO) {
							try {
								MediaMetadataRetriever retriever = new MediaMetadataRetriever();
								retriever.setDataSource(f.getAbsolutePath());
								String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
								long timeInMsec = Long.parseLong(time);
								localMedia.setDuration(timeInMsec);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
                        allImages.add(localMedia);
                        images.add(localMedia);
                    }
					Collections.sort(images, new Comparator<LocalMedia>() {
						@Override
						public int compare(LocalMedia lhs, LocalMedia rhs) {
							//descending order
							return ((Long)rhs.getLastUpdateAt()).compareTo(lhs.getLastUpdateAt());
						}
					});
                    if (images.size() > 0) {
                        localMediaFolder.setImages(images);
                        localMediaFolder.setImageNum(localMediaFolder.getImages().size());
                        imageFolders.add(localMediaFolder);
                    }
                }

				Collections.sort(allImages, new Comparator<LocalMedia>() {
					@Override
					public int compare(LocalMedia lhs, LocalMedia rhs) {
						//descending order
						return ((Long)rhs.getLastUpdateAt()).compareTo(lhs.getLastUpdateAt());
					}
				});
                allImageFolder.setImages(allImages);
                allImageFolder.setImageNum(allImageFolder.getImages().size());
				if(!allImages.isEmpty()) {
					allImageFolder.setFirstImagePath(allImages.get(0).getPath());
				}
                allImageFolder.setName(activity.getString(type == TYPE_IMAGE ? com.yongchun.library.R.string.all_image : com.yongchun.library.R.string.all_videos));
                imageFolders.add(allImageFolder);
                sortFolder(imageFolders);
                imageLoadListener.loadComplete(imageFolders);
                if (data != null) data.close();
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
            }
        });
    }

    private void sortFolder(List<LocalMediaFolder> imageFolders) {
        // 文件夹按图片数量排序
        Collections.sort(imageFolders, new Comparator<LocalMediaFolder>() {
            @Override
            public int compare(LocalMediaFolder lhs, LocalMediaFolder rhs) {
                if (lhs.getImages() == null || rhs.getImages() == null) {
                    return 0;
                }
                int lsize = lhs.getImageNum();
                int rsize = rhs.getImageNum();
                return lsize == rsize ? 0 : (lsize < rsize ? 1 : -1);
            }
        });
    }

    private LocalMediaFolder getImageFolder(String path, List<LocalMediaFolder> imageFolders) {
        File imageFile = new File(path);
        File folderFile = imageFile.getParentFile();

        for (LocalMediaFolder folder : imageFolders) {
            if (folder.getName().equals(folderFile.getName())) {
                return folder;
            }
        }
        LocalMediaFolder newFolder = new LocalMediaFolder();
        newFolder.setName(folderFile.getName());
        newFolder.setPath(folderFile.getAbsolutePath());
        newFolder.setFirstImagePath(path);
        return newFolder;
    }

    public interface LocalMediaLoadListener {
        void loadComplete(List<LocalMediaFolder> folders);
    }

}
