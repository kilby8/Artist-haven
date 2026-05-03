package com.artisthaven.app.presentation.canvas;

import android.content.Context;
import com.artisthaven.app.domain.command.CommandHistory;
import com.artisthaven.app.domain.repository.ProjectRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class CanvasViewModel_Factory implements Factory<CanvasViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<ProjectRepository> projectRepositoryProvider;

  private final Provider<CommandHistory> commandHistoryProvider;

  public CanvasViewModel_Factory(Provider<Context> contextProvider,
      Provider<ProjectRepository> projectRepositoryProvider,
      Provider<CommandHistory> commandHistoryProvider) {
    this.contextProvider = contextProvider;
    this.projectRepositoryProvider = projectRepositoryProvider;
    this.commandHistoryProvider = commandHistoryProvider;
  }

  @Override
  public CanvasViewModel get() {
    return newInstance(contextProvider.get(), projectRepositoryProvider.get(), commandHistoryProvider.get());
  }

  public static CanvasViewModel_Factory create(Provider<Context> contextProvider,
      Provider<ProjectRepository> projectRepositoryProvider,
      Provider<CommandHistory> commandHistoryProvider) {
    return new CanvasViewModel_Factory(contextProvider, projectRepositoryProvider, commandHistoryProvider);
  }

  public static CanvasViewModel newInstance(Context context, ProjectRepository projectRepository,
      CommandHistory commandHistory) {
    return new CanvasViewModel(context, projectRepository, commandHistory);
  }
}
