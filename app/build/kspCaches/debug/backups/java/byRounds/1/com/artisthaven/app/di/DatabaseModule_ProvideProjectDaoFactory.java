package com.artisthaven.app.di;

import com.artisthaven.app.data.local.AppDatabase;
import com.artisthaven.app.data.local.dao.ProjectDao;
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
public final class DatabaseModule_ProvideProjectDaoFactory implements Factory<ProjectDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideProjectDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ProjectDao get() {
    return provideProjectDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideProjectDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideProjectDaoFactory(dbProvider);
  }

  public static ProjectDao provideProjectDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideProjectDao(db));
  }
}
