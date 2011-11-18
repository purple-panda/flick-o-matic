package edu.ualr.swe;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity implements OnItemClickListener {
    
    private GridView sdcardImages;
    private ImageAdapter imageAdapter;
    private Display display;
    ImageView img;
    Bitmap image;
    ArrayList urlsArray = new ArrayList();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        // Request progress bar
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        setupViews();
        setProgressBarIndeterminateVisibility(true); 
        loadImages();
        
        
        /****get img urls from flickr***/
        
        
        //TODO: make user id dynamic
        final HttpGet get = new HttpGet("http://api.flickr.com/services/rest/?&method=flickr.people.getPublicPhotos&api_key=8d7dec18e1e325fa0df671b184ff91db&user_id=69944181@N02&format=json&nojsoncallback=1");
        HttpClient httpclient = new DefaultHttpClient();
	    ResponseHandler<String> responseHandler = new BasicResponseHandler();
	    String jsonResponse="";
	    
		try {		
			jsonResponse = httpclient.execute(get, responseHandler);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			jsonResponse = "IOException";
			e.printStackTrace();
		}
		
		urlsArray = buildUrlsArray(jsonResponse);
    }
    
   protected void onDestroy() {
        super.onDestroy();
        final GridView grid = sdcardImages;
        final int count = grid.getChildCount();
        ImageView v = null;
        for (int i = 0; i < count; i++) {
            v = (ImageView) grid.getChildAt(i);
            ((BitmapDrawable) v.getDrawable()).setCallback(null);
        }
    }

   private ArrayList<?> buildUrlsArray(String jsonResponse) {	   
	   ArrayList<String> farmsArray = new ArrayList<String>();
       ArrayList<String> serversArray = new ArrayList<String>();
       ArrayList<String> idsArray = new ArrayList<String>();
       ArrayList<String> secretsArray = new ArrayList<String>();
       ArrayList localUrlsArray =new ArrayList();
	   JSONObject jObject = null;
		
	   try {
			jObject = new JSONObject(jsonResponse);
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		JSONObject photosObject = null;
		try {
			photosObject = jObject.getJSONObject("photos");
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}	
		
		JSONArray photoArray = null;
		try {
			photoArray = photosObject.getJSONArray("photo");
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		int interestsCount = photoArray.length();
	
		//String interestsBody = "";
		for (int i=0; i<interestsCount; i++)
		{
			try {
				farmsArray.add(photoArray.getJSONObject(i).getString("farm").toString());
				serversArray.add(photoArray.getJSONObject(i).getString("server").toString());
				idsArray.add(photoArray.getJSONObject(i).getString("id").toString());
				secretsArray.add(photoArray.getJSONObject(i).getString("secret").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	
		for (int i = 0; i < farmsArray.size(); i++) {
			localUrlsArray.add("http://farm"+ farmsArray.get(i) +".static.flickr.com/" + serversArray.get(i) + "/"+ idsArray.get(i) +"_"+ secretsArray.get(i) +"_t.jpg");
		}
		return localUrlsArray;
   }
   
    private void setupViews() {
        sdcardImages = (GridView) findViewById(R.id.sdcard);
        sdcardImages.setNumColumns(display.getWidth()/95);
        sdcardImages.setClipToPadding(false);
        sdcardImages.setOnItemClickListener(Main.this);
        imageAdapter = new ImageAdapter(getApplicationContext()); 
        sdcardImages.setAdapter(imageAdapter);
    }

    private void loadImages() {
        final Object data = getLastNonConfigurationInstance();
        if (data == null) {
            new LoadImagesFromSDCard().execute();
        } else {
            final LoadedImage[] photos = (LoadedImage[]) data;
            if (photos.length == 0) {
                new LoadImagesFromSDCard().execute();
            }
            for (LoadedImage photo : photos) {
                addImage(photo);
            }
        }
    }

    
    private void addImage(LoadedImage... value) {
        for (LoadedImage image : value) {
            imageAdapter.addPhoto(image);
            imageAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        final GridView grid = sdcardImages;
        final int count = grid.getChildCount();
        final LoadedImage[] list = new LoadedImage[count];

        for (int i = 0; i < count; i++) {
            final ImageView v = (ImageView) grid.getChildAt(i);
            list[i] = new LoadedImage(((BitmapDrawable) v.getDrawable()).getBitmap());
        }

        return list;
    }

    class LoadImagesFromSDCard extends AsyncTask<Object, LoadedImage, Object> {
    	@Override
    	protected Object doInBackground(Object... params) {
            //setProgressBarIndeterminateVisibility(true); 
            Bitmap bitmap = null;
            Bitmap newBitmap = null;
            Uri uri = null;            
         
            // Set up an array of the Image ID column we want
            String[] projection = {
            		MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN};
            // Create the cursor pointing to the SDCard
            Cursor cursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all rows
                    null,       
                    null); 
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int size = cursor.getCount();
            // If size is 0, there are no images on the SD Card.
            if (size == 0) {
                //No Images available, post some message to the user
            }
            int imageID = 0;
            
            for (int i = 0; i < size; i++) {
                cursor.moveToPosition(i);
                
               imageID = cursor.getInt(columnIndex);
               Long timestamp = 1321403384000l;
               Long date = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN));
               uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + imageID);
               
               if (date > timestamp)
                	{
                		
                		try {
                			bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                			if (bitmap != null) {
                				newBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
                				bitmap.recycle();
                				if (newBitmap != null) {
                					publishProgress(new LoadedImage(newBitmap));
                				}
                			}
                } catch (IOException e) {
                    //Error fetching image, try to recover
                }
                	}
            }
            cursor.close();
            
          /*load flickr images*/
            InputStream is = null;
            for (Object url : urlsArray)  {
	            try {
	            	is = (InputStream) new URL((String) url).getContent();
	            } catch (MalformedURLException e) {
	            	// TODO Auto-generated catch block
	            	e.printStackTrace();
	            } catch (IOException e) {
	            	// TODO Auto-generated catch block
	            	e.printStackTrace();
	            }
           
	            bitmap = BitmapFactory.decodeStream(is);
	            if (bitmap != null) {
	            	newBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
	            	bitmap.recycle();
	            	if (newBitmap != null) {
	            		publishProgress(new LoadedImage(newBitmap));
	            	}
	            }
            }
 	
            return null;
        }
   
        @Override
        public void onProgressUpdate(LoadedImage... value) {
            addImage(value);
        }
  
        @Override
        protected void onPostExecute(Object result) {
            setProgressBarIndeterminateVisibility(false);
        }
    }

    class ImageAdapter extends BaseAdapter {

        private Context mContext; 
        private ArrayList<LoadedImage> photos = new ArrayList<LoadedImage>();

        public ImageAdapter(Context context) { 
            mContext = context; 
        } 

        public void addPhoto(LoadedImage photo) { 
            photos.add(photo); 
        } 

        public int getCount() { 
            return photos.size(); 
        } 

        public Object getItem(int position) { 
            return photos.get(position); 
        } 

        public long getItemId(int position) { 
            return position; 
        } 

        public View getView(int position, View convertView, ViewGroup parent) { 
            final ImageView imageView; 
            if (convertView == null) { 
                imageView = new ImageView(mContext); 
            } else { 
                imageView = (ImageView) convertView; 
            } 
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setPadding(1, 1, 1, 1);
            imageView.setImageBitmap(photos.get(position).getBitmap());
            return imageView; 
        } 
    }

    private static class LoadedImage {
        Bitmap mBitmap;

        LoadedImage(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        public Bitmap getBitmap() {
            return mBitmap;
        }
    }

    
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {        
        int columnIndex = 0;
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, 
                null, 
                null);
        if (cursor != null) {
            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToPosition(position);
            String imagePath = cursor.getString(columnIndex); 

            FileInputStream is = null;
            BufferedInputStream bis = null;
            try {
                is = new FileInputStream(new File(imagePath));
                bis = new BufferedInputStream(is);
                Bitmap bitmap = BitmapFactory.decodeStream(bis);
                Bitmap useThisBitmap = Bitmap.createScaledBitmap(bitmap, parent.getWidth(), parent.getHeight(), true);
                bitmap.recycle();
            } 
            catch (Exception e) {
                //Try to recover
            }
            finally {
                try {
                    if (bis != null) {
                        bis.close();
                    }
                    if (is != null) {
                        is.close();
                    }
                    cursor.close();
                    projection = null;
                } catch (Exception e) {
                }
            }
        }
    }
}
