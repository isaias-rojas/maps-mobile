package com.example.projectmaps.data.repository

import com.example.projectmaps.data.local.RouteDao
import com.example.projectmaps.data.local.RouteEntity
import com.example.projectmaps.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouteRepository(private val routeDao: RouteDao) {

    fun getAllRoutes(): Flow<List<Route>> {
        return routeDao.getAllRoutes().map { entities ->
            entities.map { it.toRoute() }
        }
    }

    suspend fun getRouteById(routeId: Long): Route? {
        return routeDao.getRouteById(routeId)?.toRoute()
    }

    suspend fun saveRoute(route: Route): Long {
        return routeDao.insertRoute(RouteEntity.fromRoute(route))
    }

    suspend fun deleteRoute(routeId: Long) {
        routeDao.deleteRoute(routeId)
    }
}