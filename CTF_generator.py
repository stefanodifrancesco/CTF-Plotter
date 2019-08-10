import string
from babeltrace import CTFWriter as btw
import tempfile
import time



# legge il file passato e aggiunge gli elementi alla trace
def load_numbers(filename):
    global number_of_events
    in_file = open(filename, "r")  # apro il file
    while 1:
        in_line = in_file.readline()  # leggo il file per linee
        if len(in_line) == 0:  # se è pari a 0, vuol dire che ho finito di leggere ed esco
            break
        
        # tolgo l'ultimo elemento perchè è quello che manda a capo
        in_line = in_line[:-1]
        # splitto contenuto linea dividendo elementi separati da spazio
        [ID, ts] = str.split(in_line, " ")
        event = btw.Event(event_class)  # creo un evento
        # setto il payload per l'ID (cast altrimenti non va)
        event.payload('ID_field').value = int(ID)
        event.payload('timestamp_field').value = int(
            ts)  # setto payload per timestamp
        # append our event to stream_profiler_1 stream
        stream.append_event(event)
        number_of_events += 1
    in_file.close()


# prendiamo il nome del percorso file da aprire
filename = "log.txt"

# temporary directory holding the CTF trace
trace_path = "/home/ctf"

print('trace path: {}'.format(trace_path))

while 1:
    number_of_events = 0
    # our writer
    writer = btw.Writer(trace_path)
    writer.byte_order = 2  # setto modalità big endian, info trovata su babeltrace.h

    # create one default clock and register it to the writer
    # the default frequency is 1 Ghz, that increments clock each nanosecond
    clock = btw.Clock('my_clock')
    clock.description = 'this is my clock'
    #clock.offset_seconds = 18
    #clock.time = 18
    writer.add_clock(clock)

    # create stream_profiler_1 stream class and assign our clock to it
    stream_class = btw.StreamClass('stream_profiler_1')
    stream_class.clock = clock

    # create response_time event class, that stores all the response times collected by profiler_1
    event_class = btw.EventClass('response_time')

    # create one 8-bit unsigned integer field. This will be used for code ID.
    int32_field_decl = btw.IntegerFieldDeclaration(32)
    int32_field_decl.signed = False
    # add this field declaration to response_time event class
    event_class.add_field(int32_field_decl, 'ID_field')

    # create one 32-bit signed integer field. This will be used for code ID.
    int64_field_decl = btw.IntegerFieldDeclaration(64)
    int64_field_decl.signed = False
    # add this field declaration to response_time event class
    event_class.add_field(int64_field_decl, 'timestamp_field')

    # register response_time event class to stream_profiler_1 stream class
    stream_class.add_event_class(event_class)
    # create stream_profiler_1 stream
    stream = writer.create_stream(stream_class)

    load_numbers(filename)
    stream.flush()
    print("written " + str(number_of_events) + " events to stream")
    print("wait 0.5 seconds")
    time.sleep(0.5)
