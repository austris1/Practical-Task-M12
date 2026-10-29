package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);

        Animal rex = entityManager.persist(newAnimal("Rex", AnimalType.DOG));
        Animal max = entityManager.persist(newAnimal("Max", AnimalType.DOG));
        Animal mia = entityManager.persist(newAnimal("Mia", AnimalType.CAT));

        entityManager.persist(newRecord(rex, "recent-dog@example.com", since.plusDays(10)));
        entityManager.persist(newRecord(max, "old-dog@example.com", since.minusDays(10)));
        entityManager.persist(newRecord(mia, "recent-cat@example.com", since.plusDays(10)));
        entityManager.flush();

        List<AdoptionRecord> result =
                adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(result)
                .extracting(AdoptionRecord::getAdopterEmail)
                .containsExactly("recent-dog@example.com");
    }

    private Animal newAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return animal;
    }

    private AdoptionRecord newRecord(Animal animal, String email, LocalDateTime adoptedAt) {
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(adoptedAt);
        return record;
    }
}
