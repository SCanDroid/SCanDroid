package activity.model;

import android.app.Activity;
import android.os.Bundle;

public class ActivityModelActivity extends Activity {
        /*   |, /, \ flow down
     *   ^ flow up , <= flow left, => flow right
     *
     *                     onCreate(...)
     *                          |
     *  ================>    onStart()
     *  ^                  /          \
     *  ^            onStop() <====   onResume()  <======
     *  ^              /   \      ^<====      |         ^
     *  ^ <= onRestart()  onDestroy()  ^<= onPause() => ^
     *                         |
     *                       (kill) 
     *                       
     */
	public void ActivityModel() {
    	//is null correct for savedInstanceState?
		//com.galois.ReadsContactApp.ReadsContactApp rca = new com.galois.ReadsContactApp.ReadsContactApp();
		onCreate(null);
    	while(true) { //while loop #1
    		onStart();
    		int br1 = (new Double(Math.floor(2*Math.random()))).intValue();
    		if (br1 == 0) {
    			onStop();
        		int br2 = (new Double(Math.floor(2*Math.random()))).intValue();
        		if (br2 == 0) {
        			onRestart();
        			continue;
        		}
        		else {
        			onDestroy();
        			break; //break out of loop #1
        		}
    		}
    		else {
    			while (true) { //while loop #2
    				onResume();
    	    		int br3 = (new Double(Math.floor(2*Math.random()))).intValue();
    	    		if (br3 == 0) {
    	    			break; //break out of loop #2
    	    		}
    	    		else {
    	    			onPause();
    	    			continue;
    	    		}
    			}
    			onStop();
        		int br4 = (new Double(Math.floor(2*Math.random()))).intValue();
        		if (br4 == 0) {
        			onRestart();
        			continue;
        		}
        		else {
        			onDestroy();
        			break; //break out of loop #1
        		}
    		}
    	}
    }
}