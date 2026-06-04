package com.example.blue_book.datastore

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    // SessionDataStore 构造函数已用 @Inject 标注，Hilt 自动发现并注入
}
