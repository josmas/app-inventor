package com.google.appinventor.components.runtime;

import android.util.Log;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.JsonUtil;
import org.json.JSONException;

import android.os.Handler;

import java.util.concurrent.atomic.AtomicReference;


/**
 * Component to interact with a Firebase App.
 *
 * @author josmasflores@gmail.com (Jose Dominguez)
 */
@DesignerComponent(version = YaVersion.FIREBASE_COMPONENT_VERSION, description =
    "A non-visible component that enables communication " +
    "with <a href=\"http://www.firebase.com\" target=\"_blank\">Firebase</a>. ",
    category = ComponentCategory.STORAGE, nonVisible = true, iconName = "images/firebase.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET")
@UsesLibraries(libraries = "firebase.jar")
public final class FirebaseDB extends AndroidNonvisibleComponent implements Component {


  private static final String LOG_TAG = "FIREBASE";
  private static final String DEFAULT_URL = "https://androiduitest.firebaseio.com/comps";
  private Handler androidUIHandler;
  private String firebaseUrl = "";
  private Firebase myFirebaseRef;

  public FirebaseDB(ComponentContainer container) {
    super(container.$form());

    androidUIHandler = new Handler();

    Firebase.setAndroidContext(container.$context());

    if (firebaseUrl == ""){
      //TODO (jos) is this actually ok in a constructor???
      form.dispatchErrorOccurredEvent(this, "FirebaseDB",
          ErrorMessages.ERROR_NO_FIREBASE_URL);
    }
    else {
      try{

        myFirebaseRef = new Firebase("https://androiduitest.firebaseio.com/comps");
      }
      catch (Exception ex){
        //TODO (jos) make this Exc more explicit, and give it its own error. FIXME
        form.dispatchErrorOccurredEvent(this, "FirebaseDB",
            ErrorMessages.ERROR_NO_FIREBASE_URL);
      }
    }

  }

  /**
   * FirebaseUrl property getter method.
   */
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public String FirebaseUrl() {
    return firebaseUrl;
  }

  /**
   * ConsumerKey property setter method: sets the consumer key to be used when
   * authorizing with Twitter via OAuth.
   *
   * @param firebaseUrl
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "The the consumer key to be used when authorizing with Twitter via OAuth.")
  public void FirebaseUrl(String firebaseUrl) {
    this.firebaseUrl = firebaseUrl;
  }

  @SimpleFunction
  public void DoAll() {
    try {
      //TODO (jos) put this ref out of this method - FIXME
      Firebase myFirebaseRef = new Firebase("https://androiduitest.firebaseio.com/comps");
      //WRITE
      myFirebaseRef.child("message").setValue("Do you have data? You'll love Firebase.");

      //READ
      myFirebaseRef.child("message").addValueEventListener(new ValueEventListener() {

        @Override
        public void onDataChange(DataSnapshot snapshot) {
          System.out.println(snapshot.getValue() + " noway");
        }

        @Override
        public void onCancelled(FirebaseError error) {
        }

      });
    }
    catch (Exception ex){
      Log.e("FB", "Exception trying to talk to the DB: " + ex.getMessage());
      Log.e("FB", "Exception trying to talk to the DB: " + ex);
    }

  }

  @SimpleFunction
  public void SetValue(final String tag, Object valueToStore) {
    try {
      if(valueToStore != null) {
        valueToStore = JsonUtil.getJsonRepresentation(valueToStore);
      }
    } catch(JSONException e) {
      throw new YailRuntimeError("Value failed to convert to JSON.", "JSON Creation Error.");
    }

    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here??????");
    Log.e(LOG_TAG, "what is null here?????? " + tag);
    Log.e(LOG_TAG, "what is null here?????? " + valueToStore);
    Log.e(LOG_TAG, "what is null here?????? " + myFirebaseRef);
    Firebase ref = new Firebase(firebaseUrl);
    // perform the store operation
    ref.child(tag).setValue(valueToStore);
  }


  @SimpleFunction
  public void GetValue(final String tag, final Object valueIfTagNotThere) {
    Firebase ref = new Firebase(firebaseUrl);
    ref.child(tag).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(final DataSnapshot snapshot) {
        final AtomicReference<Object> value = new AtomicReference<Object>();

        // Set value to either the JSON from the Firebase
        // or the JSON representation of valueIfTagNotThere
        try {
          if (snapshot.exists()) {
            value.set(snapshot.getValue());
          } else {
            value.set(JsonUtil.getJsonRepresentation(valueIfTagNotThere));
          }
        } catch (JSONException e) {
          throw new YailRuntimeError("Value failed to convert to JSON.", "JSON Creation Error.");
        }

        androidUIHandler.post(new Runnable() {
          public void run() {
            GotValue(tag, value.get());
          }
        });
      }

      @Override
      public void onCancelled(final FirebaseError error) {
        androidUIHandler.post(new Runnable() {
          public void run() {
            // Signal an event to indicate that an error occurred.
            // We post this to run in the Application's main
            // UI thread.
            FirebaseError(error.getMessage());
          }
        });
      }
    });
  }

  @SimpleEvent
  public void GotValue(String tag, Object value) {
    try {
      if(value != null && value instanceof String) {
        value = JsonUtil.getObjectFromJson((String) value);
      }
    } catch(JSONException e) {
      throw new YailRuntimeError("Value failed to convert from JSON.", "JSON Retrieval Error.");
    }

    // Invoke the application's "GotValue" event handler
    EventDispatcher.dispatchEvent(this, "GotValue", tag, value);
  }

  @SimpleEvent
  public void FirebaseError(String message) {
    // Log the error message for advanced developers
    Log.e(LOG_TAG, message);

    // Invoke the application's "FirebaseError" event handler
    EventDispatcher.dispatchEvent(this, "FirebaseError", message);
  }
}