package iai.co.il.streamer;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.android.volley.Response;

import java.io.Serializable;

/**
 * Created by Asaf on 12/03/2016.
 */
public class RequestListener<T extends Serializable> implements Response.Listener<T>{
    private String name;
    private Context context;

    public RequestListener(String name,Context context) {
        this.name=name;
        this.context=context;
    }

    @Override
    public void onResponse(T response) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(name);
        intent.putExtra("response",response);
        localBroadcastManager.sendBroadcast(intent);
    }
}
