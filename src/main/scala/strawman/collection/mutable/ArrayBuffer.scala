package strawman.collection.mutable

import java.lang.IndexOutOfBoundsException
import scala.{Array, Int, Long, Boolean, Unit, AnyRef}
import strawman.collection
import strawman.collection.{IterableFactory, IterableOnce, SeqLike, IndexedView}
import scala.Predef.intWrapper

/** Concrete collection type: ArrayBuffer */
class ArrayBuffer[A] private (initElems: Array[AnyRef], initLength: Int)
  extends IndexedOptimizedGrowableSeq[A]
    with SeqLike[A, ArrayBuffer]
    with Buildable[A, ArrayBuffer[A]]
    with Builder[A, ArrayBuffer[A]] {

  def this() = this(new Array[AnyRef](16), 0)

  private var array: Array[AnyRef] = initElems
  private var end = initLength

  /** Ensure that the internal array has at least `n` cells. */
  private def ensureSize(n: Int): Unit =
    array = RefArrayUtils.ensureSize(array, end, n)

  /** Reduce length to `n`, nulling out all dropped elements */
  private def reduceToSize(n: Int): Unit = {
    RefArrayUtils.nullElems(array, n, end)
    end = n
  }

  def apply(n: Int) = array(n).asInstanceOf[A]

  def update(n: Int, elem: A): Unit = array(n) = elem.asInstanceOf[AnyRef]

  def length = end
  override def knownSize = length

  override def view = new ArrayBufferView(array, end)

  def iterator() = view.iterator()

  def fromIterable[B](it: collection.Iterable[B]): ArrayBuffer[B] =
    ArrayBuffer.fromIterable(it)

  protected[this] def newBuilder = new ArrayBuffer[A]

  def clear() =
    end = 0

  def +=(elem: A): this.type = {
    ensureSize(end + 1)
    this(end) = elem
    end += 1
    this
  }

  /** Overridden to use array copying for efficiency where possible. */
  override def ++=(elems: IterableOnce[A]): this.type = {
    elems match {
      case elems: ArrayBuffer[_] =>
        ensureSize(length + elems.length)
        Array.copy(elems.array, 0, array, length, elems.length)
        end = length + elems.length
      case _ => super.++=(elems)
    }
    this
  }

  def result = this

  def insert(idx: Int, elem: A): Unit = {
    if (idx < 0 || idx > end) throw new IndexOutOfBoundsException
    ensureSize(end + 1)
    Array.copy(array, idx, array, idx + 1, end - idx)
    this(idx) = elem
  }

  def insertAll(idx: Int, elems: IterableOnce[A]): Unit = {
    if (idx < 0 || idx > end) throw new IndexOutOfBoundsException
    elems match {
      case elems: collection.Iterable[A] =>
        val elemsLength = elems.size
        ensureSize(length + elemsLength)
        Array.copy(array, idx, array, idx + elemsLength, end - idx)
        elems match {
          case elems: ArrayBuffer[_] =>
            Array.copy(elems.array, 0, array, idx, elemsLength)
          case _ =>
            var i = 0
            val it = elems.iterator()
            while (i < elemsLength) {
              this(idx + i) = it.next()
              i += 1
            }
        }
      case _ =>
        val buf = new ArrayBuffer() ++= elems
        insertAll(idx, buf)
    }
  }

  def remove(idx: Int): A = {
    if (idx < 0 || idx >= end) throw new IndexOutOfBoundsException
    val res = this(idx)
    Array.copy(array, idx + 1, array, idx, end - (idx + 1))
    reduceToSize(end - 1)
    res
  }

  def remove(from: Int, n: Int): Unit =
    if (n > 0) {
      if (from < 0 || from + n > end) throw new IndexOutOfBoundsException
      Array.copy(array, from + n, array, from, end - (from + n))
      reduceToSize(end - n)
    }

  override def className = "ArrayBuffer"
}

object ArrayBuffer extends IterableFactory[ArrayBuffer] {

  /** Avoid reallocation of buffer if length is known. */
  def fromIterable[B](coll: collection.Iterable[B]): ArrayBuffer[B] =
    if (coll.knownSize >= 0) {
      val array = new Array[AnyRef](coll.knownSize)
      val it = coll.iterator()
      for (i <- 0 until array.length) array(i) = it.next().asInstanceOf[AnyRef]
      new ArrayBuffer[B](array, array.length)
    }
    else new ArrayBuffer[B] ++= coll
}

class ArrayBufferView[A](val array: Array[AnyRef], val length: Int) extends IndexedView[A] {
  def apply(n: Int) = array(n).asInstanceOf[A]
  override def className = "ArrayBufferView"
}

/** An object used internally by collections backed by an extensible Array[AnyRef] */
object RefArrayUtils {

  def ensureSize(array: Array[AnyRef], end: Int, n: Int): Array[AnyRef] = {
    // Use a Long to prevent overflows
    val arrayLength: Long = array.length
    def growArray = {
      var newSize: Long = arrayLength * 2
      while (n > newSize)
        newSize = newSize * 2
      // Clamp newSize to Int.MaxValue
      if (newSize > Int.MaxValue) newSize = Int.MaxValue

      val newArray: Array[AnyRef] = new Array(newSize.toInt)
      Array.copy(array, 0, newArray, 0, end)
      newArray
    }
    if (n <= arrayLength) array else growArray
  }

  /** Remove elements of this array at indices after `sz`.
   */
  def nullElems(array: Array[AnyRef], start: Int, end: Int): Unit = {
    var i = start
    while (i < end) {
      array(i) = null
      i += 1
    }
  }
}
