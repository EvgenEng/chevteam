package ru.chevteam.onestep.reviews.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.reviews.ReviewPage;
import ru.chevteam.onestep.reviews.ReviewSort;
import ru.chevteam.onestep.reviews.Reviews;
import ru.chevteam.onestep.reviews.TripType;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Отзывы", description = "Отзывы об отелях и пляжах с фильтром по типу путешественников")
public class ReviewController {

    private final Reviews reviews;

    public ReviewController(Reviews reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/hotels/{id}/reviews")
    @Operation(summary = "Отзывы об отеле и выжимка «что хвалят / на что жалуются»")
    public ReviewPage hotel(@PathVariable UUID id,
                            @RequestParam(required = false) TripType tripType,
                            @RequestParam(defaultValue = "NEWEST") ReviewSort sort,
                            @RequestParam(defaultValue = "10") int limit,
                            @RequestParam(defaultValue = "0") int offset) {
        return reviews.find(Reviews.Subject.HOTEL, id, tripType, sort, limit, offset);
    }

    @GetMapping("/beaches/{id}/reviews")
    @Operation(summary = "Отзывы о пляже")
    public ReviewPage beach(@PathVariable UUID id,
                            @RequestParam(required = false) TripType tripType,
                            @RequestParam(defaultValue = "NEWEST") ReviewSort sort,
                            @RequestParam(defaultValue = "10") int limit,
                            @RequestParam(defaultValue = "0") int offset) {
        return reviews.find(Reviews.Subject.BEACH, id, tripType, sort, limit, offset);
    }
}
