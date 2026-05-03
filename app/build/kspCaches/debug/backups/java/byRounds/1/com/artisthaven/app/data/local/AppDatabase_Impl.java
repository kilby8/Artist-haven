package com.artisthaven.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.artisthaven.app.data.local.dao.LayerDao;
import com.artisthaven.app.data.local.dao.LayerDao_Impl;
import com.artisthaven.app.data.local.dao.ProjectDao;
import com.artisthaven.app.data.local.dao.ProjectDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile ProjectDao _projectDao;

  private volatile LayerDao _layerDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `folderName` TEXT NOT NULL, `widthPx` INTEGER NOT NULL, `heightPx` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `thumbnailPath` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `layers` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `name` TEXT NOT NULL, `isVisible` INTEGER NOT NULL, `opacity` REAL NOT NULL, `blendMode` TEXT NOT NULL, `isLocked` INTEGER NOT NULL, `bitmapPath` TEXT, `order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_layers_projectId` ON `layers` (`projectId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0aaf445a1162ef7f9887c58646209674')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `projects`");
        db.execSQL("DROP TABLE IF EXISTS `layers`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsProjects = new HashMap<String, TableInfo.Column>(8);
        _columnsProjects.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("folderName", new TableInfo.Column("folderName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("widthPx", new TableInfo.Column("widthPx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("heightPx", new TableInfo.Column("heightPx", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("thumbnailPath", new TableInfo.Column("thumbnailPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProjects = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProjects = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoProjects = new TableInfo("projects", _columnsProjects, _foreignKeysProjects, _indicesProjects);
        final TableInfo _existingProjects = TableInfo.read(db, "projects");
        if (!_infoProjects.equals(_existingProjects)) {
          return new RoomOpenHelper.ValidationResult(false, "projects(com.artisthaven.app.data.local.entity.ProjectEntity).\n"
                  + " Expected:\n" + _infoProjects + "\n"
                  + " Found:\n" + _existingProjects);
        }
        final HashMap<String, TableInfo.Column> _columnsLayers = new HashMap<String, TableInfo.Column>(9);
        _columnsLayers.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("projectId", new TableInfo.Column("projectId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("isVisible", new TableInfo.Column("isVisible", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("opacity", new TableInfo.Column("opacity", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("blendMode", new TableInfo.Column("blendMode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("isLocked", new TableInfo.Column("isLocked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("bitmapPath", new TableInfo.Column("bitmapPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLayers.put("order", new TableInfo.Column("order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLayers = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysLayers.add(new TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", Arrays.asList("projectId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesLayers = new HashSet<TableInfo.Index>(1);
        _indicesLayers.add(new TableInfo.Index("index_layers_projectId", false, Arrays.asList("projectId"), Arrays.asList("ASC")));
        final TableInfo _infoLayers = new TableInfo("layers", _columnsLayers, _foreignKeysLayers, _indicesLayers);
        final TableInfo _existingLayers = TableInfo.read(db, "layers");
        if (!_infoLayers.equals(_existingLayers)) {
          return new RoomOpenHelper.ValidationResult(false, "layers(com.artisthaven.app.data.local.entity.LayerEntity).\n"
                  + " Expected:\n" + _infoLayers + "\n"
                  + " Found:\n" + _existingLayers);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "0aaf445a1162ef7f9887c58646209674", "335badbbf37b67f4be8710cd57385c46");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "projects","layers");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `projects`");
      _db.execSQL("DELETE FROM `layers`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ProjectDao.class, ProjectDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LayerDao.class, LayerDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ProjectDao projectDao() {
    if (_projectDao != null) {
      return _projectDao;
    } else {
      synchronized(this) {
        if(_projectDao == null) {
          _projectDao = new ProjectDao_Impl(this);
        }
        return _projectDao;
      }
    }
  }

  @Override
  public LayerDao layerDao() {
    if (_layerDao != null) {
      return _layerDao;
    } else {
      synchronized(this) {
        if(_layerDao == null) {
          _layerDao = new LayerDao_Impl(this);
        }
        return _layerDao;
      }
    }
  }
}
