package com.back.domain.venue.controller

import com.back.global.annotation.ApiV1
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ApiV1
@RestController
@RequestMapping
@Tag(name = "Venue", description = "Venue API")
class VenueController
