package com.nexus.note;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 便签服务。
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final ChangeEventNotifier notifier;

    @Transactional(readOnly = true)
    public List<Note> list() {
        return noteRepository.findAll(Sort.by("updatedAt").descending());
    }

    @Transactional
    public Note create(NoteCreateRequest req) {
        Note note = new Note();
        note.setContent(req.getContent());
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        Note saved = noteRepository.save(note);
        notifier.notify(ChangeDomain.NOTE);
        return saved;
    }

    @Transactional
    public Note update(Long id, NoteUpdateRequest req) {
        Note note = getOrThrow(id);
        if (req.getContent() != null) {
            note.setContent(req.getContent());
        }
        note.setUpdatedAt(LocalDateTime.now());
        Note saved = noteRepository.save(note);
        notifier.notify(ChangeDomain.NOTE);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Note note = getOrThrow(id);
        noteRepository.delete(note);
        notifier.notify(ChangeDomain.NOTE);
    }

    private Note getOrThrow(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "便签不存在"));
    }
}
