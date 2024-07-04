package com.example.airplane_panel.media;

import android.graphics.Bitmap;

import java.util.Objects;

public class Media {
    private final String id;
    private final String name;
    private final String author;
    private final long duration;
    private Bitmap coverImageResourceBitmap = null;

    public Media(String id, String name, String author, long duration) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.duration = duration;
    }

    public Media(String id, String name, String author, long duration, Bitmap coverBitmapImg) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.duration = duration;
        this.coverImageResourceBitmap = coverBitmapImg;
    }


    public String getId() {

        return id;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public long getDuration() {
        return duration;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Media media = (Media) o;
        return duration == media.duration &&
                Objects.equals(id, media.id) &&
                Objects.equals(name, media.name) &&
                Objects.equals(author, media.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, author, duration);
    }

    public String toTimeString()
    {
        long minutes = duration / 60000;
        long seconds = (duration % 60000) / 1000;

        return String.format("%02d:%02d", minutes, seconds);
    }
    public void setCoverImageResourceBitmap(Bitmap bitmap) {
        coverImageResourceBitmap = bitmap;
    }
    public Bitmap getCoverImageResourceBitmap() {
        return coverImageResourceBitmap;
    }
}
