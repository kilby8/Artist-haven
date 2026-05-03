package com.artisthaven.app.di;

import com.artisthaven.app.data.local.AppDatabase;
import com.artisthaven.app.data.local.dao.LayerDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class DatabaseModule_ProvideLayerDaoFactory implements Factory<LayerDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideLayerDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public LayerDao get() {
    return provideLayerDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideLayerDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideLayerDaoFactory(dbProvider);
  }

  public static LayerDao provideLayerDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideLayerDao(db));
  }
}
