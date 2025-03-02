package server.poptato.todo.application;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import server.poptato.todo.domain.entity.Todo;
import server.poptato.todo.domain.repository.TodoRepository;
import server.poptato.todo.domain.value.TodayStatus;
import server.poptato.todo.domain.value.Type;
import server.poptato.user.domain.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Profile("!test")
public class TodoScheduler {
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final TodoService todoService;

    /**
     * 매일 새벽 특정 시간에 할 일 상태를 업데이트한다.
     */
    @Scheduled(cron = "${scheduling.todoCron}")
    public void updateTodoType() {
        updateTodayTodosAndSave();
        updateDeadlineTodoAsync();
    }

    /**
     * 할 일의 상태(Type)를 업데이트하고 저장한다.
     */
    @Transactional
    public void updateTodayTodosAndSave() {
        Map<Long, List<Todo>> userIdAndTodaysMap = updateTodayTodos();
        saveUpdatedTodos(userIdAndTodaysMap);
    }

    /**
     * 오늘(TODAY) 상태의 할 일 중 완료 여부에 따라 상태를 변경한다.
     * 완료된 반복 할 일은 백로그로 이동하며, 미완료된 할 일은 어제로 변경된다.
     *
     * @return 사용자 ID별 오늘의 할 일 목록
     */
    private Map<Long, List<Todo>> updateTodayTodos() {
        Map<Long, List<Todo>> userIdAndTodayTodosMap = todoRepository.findByType(Type.TODAY)
                .stream()
                .collect(Collectors.groupingBy(Todo::getUserId));

        userIdAndTodayTodosMap.forEach((userId, todos) -> {
            int startingOrder = todoRepository.findMaxBacklogOrderByUserIdOrZero(userId) + 1;

            for (Todo todo : todos) {
                if (todo.getTodayStatus() == TodayStatus.COMPLETED && todo.isRepeat()) {
                    // 완료한 반복 할 일이라면 백로그로 이동
                    todo.setType(Type.BACKLOG);
                    todo.setTodayStatus(null);
                    todo.setTodayOrder(null);
                    todo.setBacklogOrder(startingOrder++);
                } else if (todo.getTodayStatus() == TodayStatus.INCOMPLETE) {
                    // 미완료라면 모두 '어제'로 이동
                    todo.setType(Type.YESTERDAY);
                    todo.setTodayOrder(null);
                    todo.setBacklogOrder(startingOrder++);
                }
            }
        });

        return userIdAndTodayTodosMap;
    }

    /**
     * 업데이트된 할 일을 한 번에 저장한다.
     *
     * @param userIdAndTodaysMap 사용자 ID별 오늘의 할 일 목록
     */
    private void saveUpdatedTodos(Map<Long, List<Todo>> userIdAndTodaysMap) {
        userIdAndTodaysMap.values().stream()
                .flatMap(List::stream)
                .forEach(todoRepository::save);
    }

    /**
     * 마감기한이 된 할 일을 오늘 할 일(TODAY)로 변경한다.
     */
    @Async
    public void updateDeadlineTodoAsync() {
        LocalDate today = LocalDate.now();
        int batchSize = 50;

        List<Long> userIds = userRepository.findAllUserIds();
        splitListIntoBatches(userIds, batchSize).forEach(batch -> {
            todoService.processUpdateDeadlineTodos(today, batch);
        });
    }

    /**
     * 유저 ID 리스트를 배치 크기 단위로 나눈다.
     *
     * @param userIds   전체 사용자 ID 목록
     * @param batchSize 배치 크기
     * @return 배치된 유저 ID 목록
     */
    private List<List<Long>> splitListIntoBatches(List<Long> userIds, int batchSize) {
        return new ArrayList<>(userIds.stream()
                .collect(Collectors.groupingBy(i -> i / batchSize))
                .values());
    }
}
