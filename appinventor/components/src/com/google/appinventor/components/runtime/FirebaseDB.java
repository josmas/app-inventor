package com.google.appinventor.components.runtime;

import android.util.Log;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;


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


  private static final String LOG_TAG = "WebDB";
  private static final String DEFAULT_URL = "https://androiduitest.firebaseio.com/comps";

  public FirebaseDB(ComponentContainer container) {
    super(container.$form());

    Firebase.setAndroidContext(container.$context());

  }


  @SimpleFunction
  public void DoAll() {
    try {
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
}