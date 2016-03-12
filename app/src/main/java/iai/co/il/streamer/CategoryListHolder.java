package iai.co.il.streamer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Asaf on 12/03/2016.
 */
public class CategoryListHolder implements Serializable{
    private List<Category> list= new ArrayList<>();

    public CategoryListHolder(List<Category> list) {
        this.list = list;
    }

    public CategoryListHolder() {
    }

    public List<Category> getList() {
        return list;
    }

    public void setList(List<Category> list) {
        this.list = list;
    }
}
