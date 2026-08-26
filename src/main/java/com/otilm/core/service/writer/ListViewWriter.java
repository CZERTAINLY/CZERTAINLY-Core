package com.otilm.core.service.writer;

import com.otilm.core.dao.entity.ListView;
import com.otilm.core.dao.repository.ListViewRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListViewWriter {

    private final ListViewRepository listViewRepository;

    @Autowired
    public ListViewWriter(ListViewRepository listViewRepository) {
        this.listViewRepository = listViewRepository;
    }

    /**
     * Writes the view, and where it claims to be the default clears the flag on the user's previous default for the
     * same resource first. Clearing has to precede the write: the partial unique index refuses the moment a second
     * default row exists, so demoting afterwards would never be reached. The uuid is assigned here rather than at
     * persist time so the statement that clears the others can name the row being kept.
     */
    @Transactional
    public ListView save(ListView view) {
        if (view.getUuid() == null) {
            view.setUuid(UUID.randomUUID());
        }
        if (view.isDefaultView()) {
            listViewRepository.clearDefaultExcept(view.getUserUuid(), view.getResource(), view.getUuid());
        }
        return listViewRepository.save(view);
    }

    @Transactional
    public void delete(UUID uuid) {
        listViewRepository.deleteById(uuid);
    }

    @Transactional
    public int deleteAllForUser(UUID userUuid) {
        return listViewRepository.deleteByUserUuid(userUuid);
    }
}
