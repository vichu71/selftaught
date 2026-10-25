package es.vicai.studylab.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/** Cómo sale un mensaje del hilo del tutor: el mismo formato para el alumno y para el panel de su adulto tutor (F08). */
@Component
public class TutorViews {

    private final ObjectMapper json;

    public TutorViews(ObjectMapper json) {
        this.json = json;
    }

    public TutorService.MessageView view(TutorRepository.MessageRow row) {
        List<TutorService.SourceRef> sources = row.sources() == null ? null : read(row.sources());
        return new TutorService.MessageView(row.id(), row.role(), row.content(), row.status(), sources == null || sources.isEmpty() ? null : sources, row.createdAt());
    }

    private List<TutorService.SourceRef> read(String raw) {
        try {
            return json.readValue(raw, new TypeReference<List<TutorService.SourceRef>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
