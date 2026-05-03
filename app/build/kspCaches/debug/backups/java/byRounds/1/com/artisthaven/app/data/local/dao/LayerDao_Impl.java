package com.artisthaven.app.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.artisthaven.app.data.local.entity.LayerEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LayerDao_Impl implements LayerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LayerEntity> __insertionAdapterOfLayerEntity;

  private final EntityDeletionOrUpdateAdapter<LayerEntity> __updateAdapterOfLayerEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByProject;

  public LayerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLayerEntity = new EntityInsertionAdapter<LayerEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `layers` (`id`,`projectId`,`name`,`isVisible`,`opacity`,`blendMode`,`isLocked`,`bitmapPath`,`order`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LayerEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getProjectId());
        statement.bindString(3, entity.getName());
        final int _tmp = entity.isVisible() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindDouble(5, entity.getOpacity());
        statement.bindString(6, entity.getBlendMode());
        final int _tmp_1 = entity.isLocked() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        if (entity.getBitmapPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getBitmapPath());
        }
        statement.bindLong(9, entity.getOrder());
      }
    };
    this.__updateAdapterOfLayerEntity = new EntityDeletionOrUpdateAdapter<LayerEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `layers` SET `id` = ?,`projectId` = ?,`name` = ?,`isVisible` = ?,`opacity` = ?,`blendMode` = ?,`isLocked` = ?,`bitmapPath` = ?,`order` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LayerEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getProjectId());
        statement.bindString(3, entity.getName());
        final int _tmp = entity.isVisible() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindDouble(5, entity.getOpacity());
        statement.bindString(6, entity.getBlendMode());
        final int _tmp_1 = entity.isLocked() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        if (entity.getBitmapPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getBitmapPath());
        }
        statement.bindLong(9, entity.getOrder());
        statement.bindString(10, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM layers WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByProject = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM layers WHERE projectId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final LayerEntity layer, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLayerEntity.insert(layer);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final LayerEntity layer, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfLayerEntity.handle(layer);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByProject(final String projectId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByProject.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, projectId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByProject.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getByProject(final String projectId,
      final Continuation<? super List<LayerEntity>> $completion) {
    final String _sql = "SELECT * FROM layers WHERE projectId = ? ORDER BY `order` ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LayerEntity>>() {
      @Override
      @NonNull
      public List<LayerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "projectId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsVisible = CursorUtil.getColumnIndexOrThrow(_cursor, "isVisible");
          final int _cursorIndexOfOpacity = CursorUtil.getColumnIndexOrThrow(_cursor, "opacity");
          final int _cursorIndexOfBlendMode = CursorUtil.getColumnIndexOrThrow(_cursor, "blendMode");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfBitmapPath = CursorUtil.getColumnIndexOrThrow(_cursor, "bitmapPath");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final List<LayerEntity> _result = new ArrayList<LayerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LayerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProjectId;
            _tmpProjectId = _cursor.getString(_cursorIndexOfProjectId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsVisible;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVisible);
            _tmpIsVisible = _tmp != 0;
            final float _tmpOpacity;
            _tmpOpacity = _cursor.getFloat(_cursorIndexOfOpacity);
            final String _tmpBlendMode;
            _tmpBlendMode = _cursor.getString(_cursorIndexOfBlendMode);
            final boolean _tmpIsLocked;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_1 != 0;
            final String _tmpBitmapPath;
            if (_cursor.isNull(_cursorIndexOfBitmapPath)) {
              _tmpBitmapPath = null;
            } else {
              _tmpBitmapPath = _cursor.getString(_cursorIndexOfBitmapPath);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            _item = new LayerEntity(_tmpId,_tmpProjectId,_tmpName,_tmpIsVisible,_tmpOpacity,_tmpBlendMode,_tmpIsLocked,_tmpBitmapPath,_tmpOrder);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LayerEntity>> observeByProject(final String projectId) {
    final String _sql = "SELECT * FROM layers WHERE projectId = ? ORDER BY `order` ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, projectId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"layers"}, new Callable<List<LayerEntity>>() {
      @Override
      @NonNull
      public List<LayerEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "projectId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsVisible = CursorUtil.getColumnIndexOrThrow(_cursor, "isVisible");
          final int _cursorIndexOfOpacity = CursorUtil.getColumnIndexOrThrow(_cursor, "opacity");
          final int _cursorIndexOfBlendMode = CursorUtil.getColumnIndexOrThrow(_cursor, "blendMode");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfBitmapPath = CursorUtil.getColumnIndexOrThrow(_cursor, "bitmapPath");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final List<LayerEntity> _result = new ArrayList<LayerEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LayerEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProjectId;
            _tmpProjectId = _cursor.getString(_cursorIndexOfProjectId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsVisible;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVisible);
            _tmpIsVisible = _tmp != 0;
            final float _tmpOpacity;
            _tmpOpacity = _cursor.getFloat(_cursorIndexOfOpacity);
            final String _tmpBlendMode;
            _tmpBlendMode = _cursor.getString(_cursorIndexOfBlendMode);
            final boolean _tmpIsLocked;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_1 != 0;
            final String _tmpBitmapPath;
            if (_cursor.isNull(_cursorIndexOfBitmapPath)) {
              _tmpBitmapPath = null;
            } else {
              _tmpBitmapPath = _cursor.getString(_cursorIndexOfBitmapPath);
            }
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            _item = new LayerEntity(_tmpId,_tmpProjectId,_tmpName,_tmpIsVisible,_tmpOpacity,_tmpBlendMode,_tmpIsLocked,_tmpBitmapPath,_tmpOrder);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
