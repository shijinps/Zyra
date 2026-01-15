package com.zyra.app.data.brain;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Long;
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
public final class BrainDao_Impl implements BrainDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BrainTask> __insertionAdapterOfBrainTask;

  private final EntityInsertionAdapter<BrainTaskStep> __insertionAdapterOfBrainTaskStep;

  private final EntityDeletionOrUpdateAdapter<BrainTask> __deletionAdapterOfBrainTask;

  private final SharedSQLiteStatement __preparedStmtOfDeleteStepsForTask;

  public BrainDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBrainTask = new EntityInsertionAdapter<BrainTask>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `brain_tasks` (`id`,`name`,`triggerPhrase`) VALUES (nullif(?, 0),?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BrainTask entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getTriggerPhrase());
      }
    };
    this.__insertionAdapterOfBrainTaskStep = new EntityInsertionAdapter<BrainTaskStep>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `brain_task_steps` (`id`,`taskId`,`order`,`actionType`,`actionData`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BrainTaskStep entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTaskId());
        statement.bindLong(3, entity.getOrder());
        statement.bindString(4, __BrainActionType_enumToString(entity.getActionType()));
        statement.bindString(5, entity.getActionData());
      }
    };
    this.__deletionAdapterOfBrainTask = new EntityDeletionOrUpdateAdapter<BrainTask>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `brain_tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BrainTask entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteStepsForTask = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM brain_task_steps WHERE taskId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertTask(final BrainTask task, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfBrainTask.insertAndReturnId(task);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertStep(final BrainTaskStep step, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBrainTaskStep.insert(step);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTask(final BrainTask task, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBrainTask.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertTaskWithSteps(final BrainTask task, final List<BrainTaskStep> steps,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> BrainDao.DefaultImpls.insertTaskWithSteps(BrainDao_Impl.this, task, steps, __cont), $completion);
  }

  @Override
  public Object deleteStepsForTask(final int taskId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteStepsForTask.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, taskId);
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
          __preparedStmtOfDeleteStepsForTask.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BrainTask>> getAllTasks() {
    final String _sql = "SELECT * FROM brain_tasks";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"brain_tasks"}, new Callable<List<BrainTask>>() {
      @Override
      @NonNull
      public List<BrainTask> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTriggerPhrase = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerPhrase");
          final List<BrainTask> _result = new ArrayList<BrainTask>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BrainTask _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpTriggerPhrase;
            _tmpTriggerPhrase = _cursor.getString(_cursorIndexOfTriggerPhrase);
            _item = new BrainTask(_tmpId,_tmpName,_tmpTriggerPhrase);
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

  @Override
  public Object getTaskById(final int id, final Continuation<? super BrainTask> $completion) {
    final String _sql = "SELECT * FROM brain_tasks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BrainTask>() {
      @Override
      @Nullable
      public BrainTask call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTriggerPhrase = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerPhrase");
          final BrainTask _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpTriggerPhrase;
            _tmpTriggerPhrase = _cursor.getString(_cursorIndexOfTriggerPhrase);
            _result = new BrainTask(_tmpId,_tmpName,_tmpTriggerPhrase);
          } else {
            _result = null;
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
  public Object getTaskByTrigger(final String phrase,
      final Continuation<? super BrainTask> $completion) {
    final String _sql = "SELECT * FROM brain_tasks WHERE triggerPhrase = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, phrase);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BrainTask>() {
      @Override
      @Nullable
      public BrainTask call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTriggerPhrase = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerPhrase");
          final BrainTask _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpTriggerPhrase;
            _tmpTriggerPhrase = _cursor.getString(_cursorIndexOfTriggerPhrase);
            _result = new BrainTask(_tmpId,_tmpName,_tmpTriggerPhrase);
          } else {
            _result = null;
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
  public Object getStepsForTask(final int taskId,
      final Continuation<? super List<BrainTaskStep>> $completion) {
    final String _sql = "SELECT * FROM brain_task_steps WHERE taskId = ? ORDER BY `order` ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BrainTaskStep>>() {
      @Override
      @NonNull
      public List<BrainTaskStep> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "taskId");
          final int _cursorIndexOfOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "order");
          final int _cursorIndexOfActionType = CursorUtil.getColumnIndexOrThrow(_cursor, "actionType");
          final int _cursorIndexOfActionData = CursorUtil.getColumnIndexOrThrow(_cursor, "actionData");
          final List<BrainTaskStep> _result = new ArrayList<BrainTaskStep>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BrainTaskStep _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpTaskId;
            _tmpTaskId = _cursor.getInt(_cursorIndexOfTaskId);
            final int _tmpOrder;
            _tmpOrder = _cursor.getInt(_cursorIndexOfOrder);
            final BrainActionType _tmpActionType;
            _tmpActionType = __BrainActionType_stringToEnum(_cursor.getString(_cursorIndexOfActionType));
            final String _tmpActionData;
            _tmpActionData = _cursor.getString(_cursorIndexOfActionData);
            _item = new BrainTaskStep(_tmpId,_tmpTaskId,_tmpOrder,_tmpActionType,_tmpActionData);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __BrainActionType_enumToString(@NonNull final BrainActionType _value) {
    switch (_value) {
      case OPEN_APP: return "OPEN_APP";
      case CLICK_TEXT: return "CLICK_TEXT";
      case SCROLL: return "SCROLL";
      case INPUT_TEXT: return "INPUT_TEXT";
      case WAIT: return "WAIT";
      case SAY: return "SAY";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private BrainActionType __BrainActionType_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "OPEN_APP": return BrainActionType.OPEN_APP;
      case "CLICK_TEXT": return BrainActionType.CLICK_TEXT;
      case "SCROLL": return BrainActionType.SCROLL;
      case "INPUT_TEXT": return BrainActionType.INPUT_TEXT;
      case "WAIT": return BrainActionType.WAIT;
      case "SAY": return BrainActionType.SAY;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
