package iai.co.il.streamer;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.io.Serializable;


public class ErrorListener<T extends Serializable> implements Response.ErrorListener{
    private String name;
    private Context context;

    public ErrorListener(String name, Context context) {
        this.name=name;
        this.context=context;
    }


    @Override
    public void onErrorResponse(VolleyError error) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(name);
        intent.putExtra("response",error.toString());
        localBroadcastManager.sendBroadcast(intent);

    }
}
