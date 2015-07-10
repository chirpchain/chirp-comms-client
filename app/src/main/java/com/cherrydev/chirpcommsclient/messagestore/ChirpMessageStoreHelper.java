package com.cherrydev.chirpcommsclient.messagestore;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;

/**
 * Created by alannon on 2015-07-09.
 */
public class ChirpMessageStoreHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "chirpDb.db";
    public static final String TABLE_MESSAGES = "messages";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SENDER = "SENDER";
    public static final String COLUMN_RECIPIENT = "RECIPIENT";
    public static final String COLUMN_MESSAGE = "MESSAGE";
    public static final String COLUMN_DATE = "DATE_RECEIVED";

    public ChirpMessageStoreHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createMessageTableSql =
                "CREATE TABLE " + TABLE_MESSAGES + " ( " +
                        COLUMN_ID +" INTEGER PRIMARY KEY, " +
                        COLUMN_SENDER + " TEXT, " +
                        COLUMN_RECIPIENT +" TEXT, " +
                        COLUMN_MESSAGE + " TEXT, " +
                        COLUMN_DATE + " INTEGER)";
        db.execSQL(createMessageTableSql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE " + TABLE_MESSAGES);
        onCreate(db);
    }
}
