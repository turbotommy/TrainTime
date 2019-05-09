package se.tomlab;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

public class JSONMap<String, JSONObject> extends HashMap implements Serializable {
    private static final long serialVersionUID = 7526471155622776148L;

    private String sTrain;
    private JSONObject jsonObject;

    public JSONMap(String sTrainIn, JSONObject jsonObjectIn) {
        sTrain=sTrainIn;
        jsonObject=jsonObjectIn;
    }

    public JSONMap() {

    }

    public void putIfEmpty(String sTrainId, JSONObject joTransfer) {
        if(this.equals(sTrainId)) {
            this.replace(sTrainId,joTransfer);
        } else {
            this.put(sTrainId,joTransfer);
        }
    }

    /**
     * Always treat de-serialization as a full-blown constructor, by
     * validating the final state of the de-serialized object.
     */
    private void readObject(
            ObjectInputStream aInputStream
    ) throws ClassNotFoundException, IOException {
        //always perform the default de-serialization first
        aInputStream.defaultReadObject();
        //Object obj=aInputStream.readObject();

        //make defensive copy of the mutable Date field
        //fDateOpened = new Date(fDateOpened.getTime());

        //ensure that object state has not been corrupted or tampered with maliciously
        //validateState();
    }

    /**
     * This is the default implementation of writeObject.
     * Customise if necessary.
     */
    private void writeObject(
            ObjectOutputStream aOutputStream
    ) throws IOException {
        //perform the default serialization for all non-transient, non-static fields
        aOutputStream.defaultWriteObject();
        aOutputStream.writeObject(jsonObject.toString());
    }

}
