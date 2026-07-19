package com.ecommerce.auth.adapter.out.persistence;

import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.UserRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserMongoRepository extends MongoRepository<User, String>, UserRepository {
}
