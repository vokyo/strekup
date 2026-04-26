package com.streakup.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

class BaseEntityTest {

	@Test
	void carriesJpaAuditingMetadata() throws NoSuchFieldException {
		assertThat(BaseEntity.class).hasAnnotation(MappedSuperclass.class);
		assertThat(BaseEntity.class.getAnnotation(EntityListeners.class).value()).contains(AuditingEntityListener.class);

		assertThat(BaseEntity.class.getDeclaredField("id").isAnnotationPresent(Id.class)).isTrue();
		GeneratedValue generatedValue = BaseEntity.class.getDeclaredField("id").getAnnotation(GeneratedValue.class);
		assertThat(generatedValue.strategy()).isEqualTo(GenerationType.IDENTITY);

		assertThat(BaseEntity.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
		Column createdAtColumn = BaseEntity.class.getDeclaredField("createdAt").getAnnotation(Column.class);
		assertThat(createdAtColumn.name()).isEqualTo("created_at");
		assertThat(createdAtColumn.updatable()).isFalse();

		assertThat(BaseEntity.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
		Column updatedAtColumn = BaseEntity.class.getDeclaredField("updatedAt").getAnnotation(Column.class);
		assertThat(updatedAtColumn.name()).isEqualTo("updated_at");
	}
}
