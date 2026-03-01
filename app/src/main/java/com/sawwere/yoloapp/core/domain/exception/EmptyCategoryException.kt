package com.sawwere.yoloapp.core.domain.exception

import java.lang.RuntimeException

class EmptyCategoryException(categoryId: Long): RuntimeException("Category is empty: $categoryId")