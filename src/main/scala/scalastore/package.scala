import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter

package object scalastore {
  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }
}
