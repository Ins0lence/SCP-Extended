defmodule ProcessSpec do
  def spawn_client() do
    {:ok, spawn_link(__MODULE__, :client, [])}
  end

  def client() do
    receive do
      {"Hello", pid, data} ->
        Process.sleep(200)
        send(pid, {"Done", self(), data})
        client()
      _ ->
        IO.puts("Unexpected message received!")
    end
  end

  def spawn_server(counter, data) do
    pid = spawn_link(__MODULE__, :server, [counter, spawn_link(__MODULE__, :client, []), 0, data])
    send(pid, "Start")
    {:ok, pid}
  end
  def server(counter, clients, message_count, data) do
    receive do
      "Start" ->
        send(clients, {"Hello", self(), data})
        server(counter, clients, message_count, data)
      {"Done", pid, data} ->
        send(counter, 1)
        send(pid, {"Hello", self(), data})
        server(counter, clients, message_count+1, data)
      "Stop" ->
        exit(:kill)
      _ ->
        IO.puts("Unexpected message received!")
    end
  end
  def spawn_counter() do
    pid = spawn_link(__MODULE__, :aggregator, [0, :erlang.system_time(:millisecond), []])
    Process.register(pid, :counter)
    spawn_link(__MODULE__, :logger, [pid])
    {:ok, pid}
  end
  def aggregator(n, start_time, record) do

    receive do
      "Start" ->
        aggregator(0, :erlang.system_time(:millisecond), [])
      "Update" ->
        elapsed_time = (:erlang.system_time(:millisecond)-start_time)/1000
        aggregator(0, :erlang.system_time(:millisecond), [n/elapsed_time | record])
      "Stop" ->
        average = :lists.sum(record) / length(record)
        {:ok, file} = File.open("result.txt", [:append])
        :io.format(file, "~.3f~n", [average])
      messages_count ->
        aggregator(n+messages_count, start_time, record)
    end
  end
  def spawn_logger(counter) do
    {:ok, spawn(__MODULE__, :logger, [counter])}
  end
  def logger(counter) do
    Process.sleep(1000)
    send(counter,"Update")
    logger(counter)
  end
end
