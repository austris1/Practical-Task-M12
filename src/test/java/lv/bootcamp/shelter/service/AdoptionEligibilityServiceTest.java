package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.*;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    private Adopter adopter;
    private Animal animal;

    @BeforeEach
    void setUp() {
        adopter = new Adopter();
        adopter.setId(ADOPTER_ID);
        adopter.setEmail("anna@example.com");
        adopter.setAge(30);

        animal = new Animal();
        animal.setId(ANIMAL_ID);
        animal.setName("Rex");
        animal.setType(AnimalType.DOG);
        animal.setAge(3);
        animal.setStatus(AnimalStatus.AVAILABLE);
    }

    private void stubBoth() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    @Test
    void evaluateAdoption_shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluateAdoption_shouldRejectWhenAnimalNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluateAdoption_shouldRejectWhenAnimalNotAvailable() {
        animal.setStatus(AnimalStatus.RESERVED);
        stubBoth();

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluateAdoption_shouldRejectUnderageAdopter() {
        adopter.setAge(17);
        stubBoth();

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void evaluateAdoption_shouldApproveAdopterAgedExactly18() {
        adopter.setAge(18);
        stubBoth();

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "false, 2, true",
            "false, 3, false",
            "true, 4, true",
            "true, 5, false"
    })
    void evaluateAdoption_shouldApplyPetLimit(boolean largeProperty, int petCount, boolean expectedApproved) {
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(petCount);
        stubBoth();

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isEqualTo(expectedApproved);
        if (!expectedApproved) {
            assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
            verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
        }
    }

    @Test
    void evaluateAdoption_shouldApproveExoticAnimalWithPermit() {
        animal.setType(AnimalType.BIRD);
        adopter.setExoticPermit(true);
        stubBoth();

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void calculatePriorityScore_shouldIgnoreUnknownAnimalAge() {
        animal.setAge(null);

        assertThat(service.calculatePriorityScore(adopter, animal)).isZero();
    }
}
