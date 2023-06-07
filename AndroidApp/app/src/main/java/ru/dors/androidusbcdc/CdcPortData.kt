package ru.dors.androidusbcdc

class CdcPortData(private var id: Int, private var writeEndpoint: Int,
                  private var readEndpoint: Int
) {
    fun getId(): Int {
        return id
    }

    fun getWriteEndpoint(): Int {
        return writeEndpoint
    }

    fun getReadEndpoint(): Int {
        return readEndpoint
    }
}
