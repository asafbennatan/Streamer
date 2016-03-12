package iai.co.il.streamer;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Created by Asaf on 12/03/2016.
 */
public class VoidJsonRequest extends JsonRequest<Serializable> {

    public VoidJsonRequest(String url, Class<Serializable> clazz, Map<String, String> headers, Response.Listener<Serializable> listener, Response.ErrorListener errorListener) {
        super(url, clazz, headers, listener, errorListener);
    }

    @Override
    protected Response<Serializable> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = new String(
                    response.data,
                    HttpHeaderParser.parseCharset(response.headers));

            return Response.success(
                  null,
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (IOException e) {
            return Response.error(new ParseError(e));
        }
    }
}
