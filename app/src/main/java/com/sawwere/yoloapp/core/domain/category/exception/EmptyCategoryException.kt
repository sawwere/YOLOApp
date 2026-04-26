package com.sawwere.yoloapp.core.domain.category.exception

import java.lang.RuntimeException

class EmptyCategoryException(categoryId: Long): RuntimeException("Category is empty: $categoryId")