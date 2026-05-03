package com.artisthaven.app.di;

import com.artisthaven.app.domain.command.CommandHistory;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DatabaseModule_ProvideCommandHistoryFactory implements Factory<CommandHistory> {
  @Override
  public CommandHistory get() {
    return provideCommandHistory();
  }

  public static DatabaseModule_ProvideCommandHistoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CommandHistory provideCommandHistory() {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCommandHistory());
  }

  private static final class InstanceHolder {
    private static final DatabaseModule_ProvideCommandHistoryFactory INSTANCE = new DatabaseModule_ProvideCommandHistoryFactory();
  }
}
