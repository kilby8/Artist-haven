package com.artisthaven.app.data.repository;

import android.content.Context;
import com.artisthaven.app.data.local.dao.LayerDao;
import com.artisthaven.app.data.local.dao.ProjectDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ProjectRepositoryImpl_Factory implements Factory<ProjectRepositoryImpl> {
  private final Provider<Context> contextProvider;

  private final Provider<ProjectDao> projectDaoProvider;

  private final Provider<LayerDao> layerDaoProvider;

  public ProjectRepositoryImpl_Factory(Provider<Context> contextProvider,
      Provider<ProjectDao> projectDaoProvider, Provider<LayerDao> layerDaoProvider) {
    this.contextProvider = contextProvider;
    this.projectDaoProvider = projectDaoProvider;
    this.layerDaoProvider = layerDaoProvider;
  }

  @Override
  public ProjectRepositoryImpl get() {
    return newInstance(contextProvider.get(), projectDaoProvider.get(), layerDaoProvider.get());
  }

  public static ProjectRepositoryImpl_Factory create(Provider<Context> contextProvider,
      Provider<ProjectDao> projectDaoProvider, Provider<LayerDao> layerDaoProvider) {
    return new ProjectRepositoryImpl_Factory(contextProvider, projectDaoProvider, layerDaoProvider);
  }

  public static ProjectRepositoryImpl newInstance(Context context, ProjectDao projectDao,
      LayerDao layerDao) {
    return new ProjectRepositoryImpl(context, projectDao, layerDao);
  }
}
